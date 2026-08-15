#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prepares -- and only on request, performs -- the release upload of every jar.

    python publish/release.py                 what was built, and nothing else
    python publish/release.py --check         the full plan, asking both platforms
    python publish/release.py --upload modrinth --confirm
    python publish/release.py --upload curseforge --confirm

**Nothing is sent unless both --upload and --confirm are given.** Without them
this prints what it would do and stops. There is no default that publishes and
no "assume yes".

Where the facts come from
-------------------------
Every jar is read for its own metadata rather than described in a list beside
it: the loader, the mod version, the Minecraft range it declares and whether it
needs Fabric API all come out of the fabric.mod.json or the mods.toml inside
the file. A jar and its upload therefore cannot disagree.

Which Minecraft versions a range covers is asked of the platform instead of
guessed, so ">=1.21.6 <1.21.10" becomes whichever releases actually exist in
between -- and a new patch is picked up without editing anything here.

The changelog comes from the top section of CHANGELOG.md, so it is not written
out a second time.

The 1.20.1 jars live in a separate working tree; both are looked in.

Standard library only, on purpose: releasing the mod should not require
installing anything.
"""

import argparse
import io
import json
import mimetypes
import os
import re
import sys
import uuid
import zipfile
from urllib.error import HTTPError
from urllib.request import Request, urlopen


# --------------------------------------------------------------------------
# Where things are
# --------------------------------------------------------------------------

HERE = os.path.dirname(os.path.abspath(__file__))
MAIN = os.path.dirname(HERE)
BRANCH_1_20_1 = os.path.join(os.path.dirname(MAIN), "ontime-1.20.1-maintenance")

JAR_DIRS = [
    os.path.join(MAIN, "build", "libs"),
    os.path.join(BRANCH_1_20_1, "build", "libs"),
]

MODRINTH_API = "https://api.modrinth.com/v2"
CURSEFORGE_API = "https://minecraft.curseforge.com/api"

AGENT = "OnTime-release/1.0 (github.com/MateoF024/OnTime)"
PROJECTS = os.path.join(HERE, "projects.json")


def settings():
    """Project ids from publish/projects.json, changelog from CHANGELOG.md."""
    if not os.path.exists(PROJECTS):
        sys.exit("publish/projects.json is missing. Copy projects.example.json\n"
                 "next to it and fill in the two project ids.")
    with io.open(PROJECTS, encoding="utf-8") as handle:
        conf = json.load(handle)
    for key in ("modrinth_project_id", "curseforge_project_id"):
        if not conf.get(key) or str(conf[key]).startswith("<"):
            sys.exit("publish/projects.json still has a placeholder for %s." % key)
    conf["changelog"] = newest_changelog()
    return conf


def newest_changelog():
    """The topmost section of CHANGELOG.md, so it is never written out twice."""
    with io.open(os.path.join(MAIN, "CHANGELOG.md"), encoding="utf-8") as handle:
        sections = re.split(r"^## ", handle.read(), flags=re.M)
    if len(sections) < 2:
        sys.exit("CHANGELOG.md has no '## Version ...' section to attach.")
    return ("## " + sections[1]).strip()


def token(name):
    value = os.environ.get(name)
    if not value:
        sys.exit("%s is not set. Export it in this shell only; never put it in a file." % name)
    return value


# --------------------------------------------------------------------------
# Talking to the platforms, without pulling in a library to do it
# --------------------------------------------------------------------------

def get_json(url, headers=None):
    request = Request(url, headers=dict(headers or {}, **{"User-Agent": AGENT}))
    with urlopen(request, timeout=60) as reply:
        return json.loads(reply.read().decode("utf-8"))


def post_multipart(url, fields, filename, filepath, headers=None):
    """A multipart/form-data POST built by hand: text fields, then the jar."""
    boundary = uuid.uuid4().hex
    body = io.BytesIO()

    def write(text):
        body.write(text.encode("utf-8"))

    for name, value in fields.items():
        write("--%s\r\n" % boundary)
        write('Content-Disposition: form-data; name="%s"\r\n\r\n' % name)
        write(value)
        write("\r\n")

    kind = mimetypes.guess_type(filename)[0] or "application/java-archive"
    write("--%s\r\n" % boundary)
    write('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename)
    write("Content-Type: %s\r\n\r\n" % kind)
    with open(filepath, "rb") as handle:
        body.write(handle.read())
    write("\r\n--%s--\r\n" % boundary)

    payload = body.getvalue()
    head = dict(headers or {})
    head["User-Agent"] = AGENT
    head["Content-Type"] = "multipart/form-data; boundary=" + boundary
    head["Content-Length"] = str(len(payload))
    try:
        with urlopen(Request(url, data=payload, headers=head), timeout=600) as reply:
            return reply.status, reply.read().decode("utf-8", "replace")
    except HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")


# --------------------------------------------------------------------------
# Reading a jar
# --------------------------------------------------------------------------

class Jar(object):
    """One built jar, described by what is inside it."""

    def __init__(self, path):
        self.path = path
        self.name = os.path.basename(path)
        with zipfile.ZipFile(path) as jar:
            names = jar.namelist()
            if "fabric.mod.json" in names:
                self._read_fabric(jar.read("fabric.mod.json").decode("utf-8"))
            else:
                toml = [n for n in names if n.endswith("mods.toml")]
                if not toml:
                    raise ValueError("%s carries no loader metadata" % self.name)
                self._read_toml(jar.read(toml[0]).decode("utf-8"),
                                neoforge="neoforge.mods.toml" in toml[0])

    def _read_fabric(self, text):
        data = json.loads(text)
        self.loader = "fabric"
        self.version = data["version"]
        self.mc_range = parse_range(data["depends"]["minecraft"])
        api = data["depends"].get("fabric-api", "")
        self.needs_fabric_api = bool(api) and api != "*"

    def _read_toml(self, text, neoforge):
        self.loader = "neoforge" if neoforge else "forge"
        self.version = re.search(r'^version\s*=\s*"([^"]+)"', text, re.M).group(1)
        block = re.search(r'modId\s*=\s*"minecraft"(?:.|\n)*?versionRange\s*=\s*"([^"]+)"',
                          text)
        if not block:
            raise ValueError("%s declares no Minecraft range" % self.name)
        self.mc_range = parse_range(block.group(1))
        self.needs_fabric_api = False

    @property
    def java(self):
        """What the family compiles at, which CurseForge asks for by name."""
        low = self.mc_range[0]
        if low[0] >= 26:
            return "Java 25"
        if low[0] == 1 and low[1] <= 20:
            return "Java 17"
        return "Java 21"

    @property
    def loader_name(self):
        return {"fabric": "Fabric", "neoforge": "NeoForge", "forge": "Forge"}[self.loader]

    def span(self):
        low, high = self.mc_range
        return "%s .. <%s" % (dotted(low), dotted(high))


def dotted(parts):
    return ".".join(str(p) for p in parts)


def as_tuple(version):
    """"1.21.10" -> (1, 21, 10). Anything that is not a plain release: None."""
    if not re.match(r"^\d+(\.\d+)*$", version.strip()):
        return None
    parts = [int(chunk) for chunk in version.strip().split(".")]
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts[:3])


def parse_range(spec):
    """Both shapes the loaders use, as an inclusive-exclusive pair.

    Fabric writes ">=1.21.6 <1.21.10"; Forge and NeoForge write the Maven form
    "[1.21.6,1.21.10)". Both mean the same thing, and both are closed at the
    bottom and open at the top everywhere in this project.
    """
    maven = re.match(r"^\[([^,]+),([^)\]]+)\)$", spec.strip())
    if maven:
        return as_tuple(maven.group(1)), as_tuple(maven.group(2))
    low = re.search(r">=\s*([\d.]+)", spec)
    high = re.search(r"<\s*([\d.]+)", spec)
    if not low or not high:
        raise ValueError("cannot read the Minecraft range %r" % spec)
    return as_tuple(low.group(1)), as_tuple(high.group(1))


def covers(jar, version):
    point = as_tuple(version)
    if point is None:
        return False
    low, high = jar.mc_range
    return low <= point < high


def find_jars():
    found, missing = [], []
    for directory in JAR_DIRS:
        if not os.path.isdir(directory):
            missing.append(directory)
            continue
        for loader_dir in sorted(os.listdir(directory)):
            full = os.path.join(directory, loader_dir)
            if not os.path.isdir(full):
                continue
            for name in sorted(os.listdir(full)):
                if name.endswith(".jar") and "-sources" not in name:
                    found.append(Jar(os.path.join(full, name)))
    return found, missing


# --------------------------------------------------------------------------
# Modrinth
# --------------------------------------------------------------------------

def modrinth_releases():
    """Every released Minecraft version, newest first. Snapshots excluded."""
    tags = get_json(MODRINTH_API + "/tag/game_version")
    return [t["version"] for t in tags if t["version_type"] == "release"]


def modrinth_plan(jar, releases, fabric_api_id, conf):
    versions = [v for v in releases if covers(jar, v)]
    if not versions:
        raise ValueError("%s: Modrinth lists no release inside %s"
                         % (jar.name, jar.span()))
    oldest, newest = versions[-1], versions[0]
    label = oldest if oldest == newest else "%s-%s" % (oldest, newest)

    data = {
        "project_id": conf["modrinth_project_id"],
        "name": "OnTime %s - %s %s" % (jar.version, jar.loader_name, label),
        # Unique per file: Modrinth refuses two versions with the same number,
        # and sixteen files all called 5.0.0 would be exactly that.
        "version_number": "%s+%s.%s" % (jar.version, jar.loader, oldest),
        "changelog": conf["changelog"],
        "game_versions": versions,
        "version_type": conf.get("release_type", "release"),
        "loaders": [jar.loader],
        "featured": False,
        "dependencies": [],
    }
    if jar.needs_fabric_api and fabric_api_id:
        data["dependencies"].append({"project_id": fabric_api_id,
                                     "dependency_type": "required"})
    return data


# --------------------------------------------------------------------------
# CurseForge
# --------------------------------------------------------------------------

def curseforge_plan(jar, taxonomy, conf):
    by_name = {}
    for entry in taxonomy:
        by_name.setdefault(entry["name"], entry["id"])

    versions = sorted((name for name in by_name if covers(jar, name)),
                      key=as_tuple, reverse=True)
    if not versions:
        raise ValueError("%s: CurseForge lists no version inside %s"
                         % (jar.name, jar.span()))

    # The loader and the Java level live in the same taxonomy, so they are
    # looked up by name exactly like the game versions are.
    wanted = versions + [jar.loader_name, jar.java]
    missing = [name for name in wanted if name not in by_name]
    if missing:
        raise ValueError("%s: CurseForge does not know %s" % (jar.name, ", ".join(missing)))

    oldest, newest = versions[-1], versions[0]
    label = oldest if oldest == newest else "%s-%s" % (oldest, newest)
    metadata = {
        "changelog": conf["changelog"],
        "changelogType": "markdown",
        "displayName": "OnTime %s - %s %s" % (jar.version, jar.loader_name, label),
        "gameVersions": [by_name[name] for name in wanted],
        "releaseType": conf.get("release_type", "release"),
        "relations": {"projects": []},
    }
    if jar.needs_fabric_api:
        metadata["relations"]["projects"].append(
            {"slug": "fabric-api", "type": "requiredDependency"})
    return metadata


# --------------------------------------------------------------------------
# Doing it, or not
# --------------------------------------------------------------------------

def inventory(jars, missing):
    print("%-40s %-9s %-8s %-20s %s" % ("jar", "loader", "version", "minecraft", "java"))
    for jar in jars:
        print("%-40s %-9s %-8s %-20s %s"
              % (jar.name, jar.loader, jar.version, jar.span(), jar.java))
    print("\n%d jars." % len(jars))
    for directory in missing:
        print("NOT BUILT: %s" % directory)


def main():
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true",
                        help="ask both platforms and print exactly what would be sent")
    parser.add_argument("--upload", choices=["modrinth", "curseforge"],
                        help="which platform to upload to")
    parser.add_argument("--confirm", action="store_true",
                        help="required alongside --upload; without it nothing is sent")
    parser.add_argument("--only", metavar="TEXT",
                        help="restrict to jars whose filename contains TEXT")
    args = parser.parse_args()

    jars, missing = find_jars()
    if args.only:
        jars = [j for j in jars if args.only in j.name]
    if not jars:
        sys.exit("No jars found. Build them first.")

    if not args.check and not args.upload:
        inventory(jars, missing)
        print("\nNothing was sent. --check shows the full plan; --upload with "
              "--confirm publishes.")
        return

    if missing:
        for directory in missing:
            print("WARNING: %s was never built; its jars are not in this release."
                  % directory)
        print("")

    conf = settings()
    sending = bool(args.upload and args.confirm)

    # CurseForge will not say what it knows without a token, so a check without
    # one skips it rather than failing: the Modrinth half is still worth seeing.
    if args.check and not args.upload and not os.environ.get("CURSEFORGE_TOKEN"):
        print("(CURSEFORGE_TOKEN is not set, so the CurseForge plan is skipped)")
    elif args.upload == "curseforge" or args.check:
        head = {"X-Api-Token": token("CURSEFORGE_TOKEN")}
        taxonomy = get_json(CURSEFORGE_API + "/game/versions", head)
        print("== CurseForge")
        for jar in jars:
            plan = curseforge_plan(jar, taxonomy, conf)
            print("\n%s\n   %s\n   versions %s + %s + %s"
                  % (jar.name, plan["displayName"], len(plan["gameVersions"]) - 2,
                     jar.loader_name, jar.java))
            if jar.needs_fabric_api:
                print("   requires Fabric API")
            if args.upload == "curseforge" and sending:
                status, text = post_multipart(
                    "%s/projects/%s/upload-file" % (CURSEFORGE_API,
                                                    conf["curseforge_project_id"]),
                    {"metadata": json.dumps(plan)}, jar.name, jar.path, head)
                print("   -> %s %s" % (status, text[:200]))
                if status >= 300:
                    sys.exit("CurseForge refused %s; stopping here." % jar.name)

    if args.upload == "modrinth" or args.check:
        head = {}
        if args.upload == "modrinth":
            head["Authorization"] = token("MODRINTH_TOKEN")
        releases = modrinth_releases()
        try:
            fabric_api_id = get_json(MODRINTH_API + "/project/fabric-api")["id"]
        except Exception:
            fabric_api_id = None
            print("(could not resolve fabric-api; its dependency would be left off)")
        print("\n== Modrinth")
        for jar in jars:
            plan = modrinth_plan(jar, releases, fabric_api_id, conf)
            print("\n%s\n   %s\n   %s\n   %s"
                  % (jar.name, plan["version_number"], plan["name"],
                     ", ".join(plan["game_versions"])))
            if plan["dependencies"]:
                print("   requires Fabric API (%s)" % fabric_api_id)
            if args.upload == "modrinth" and sending:
                data = dict(plan, file_parts=["file"])
                status, text = post_multipart(
                    MODRINTH_API + "/version",
                    {"data": json.dumps(data)}, jar.name, jar.path, head)
                print("   -> %s %s" % (status, text[:200]))
                if status >= 300:
                    sys.exit("Modrinth refused %s; stopping here." % jar.name)

    if not sending:
        print("\nNothing was sent: this was a dry run.")


if __name__ == "__main__":
    main()
