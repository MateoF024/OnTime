package com.mateof24.validation;

import net.minecraft.network.chat.Component;

public class CommandValidator {

    /**
     * Valida que un comando sea válido antes de guardarlo.
     * @param command El comando a validar (sin el '/')
     * @return ValidationResult con el estado y mensaje de error si aplica
     */
    public static ValidationResult validate(String command) {
        // Comando nulo o vacío
        if (command == null || command.trim().isEmpty()) {
            return ValidationResult.error("ontime.command.validation.empty");
        }

        // Comando solo con espacios
        if (command.trim().isEmpty()) {
            return ValidationResult.error("ontime.command.validation.empty");
        }

        // Comando que empieza con '/' (error común del usuario)
        if (command.startsWith("/")) {
            return ValidationResult.error("ontime.command.validation.no_slash");
        }

        // Validar que tenga al menos una palabra (el nombre del comando)
        String[] parts = command.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return ValidationResult.error("ontime.command.validation.invalid_format");
        }

        // Validar caracteres permitidos en el nombre del comando
        String commandName = parts[0];
        if (!commandName.matches("[a-zA-Z0-9_-]+")) {
            return ValidationResult.error("ontime.command.validation.invalid_characters");
        }

        return ValidationResult.success();
    }

    /**
     * Resultado de la validación
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorKey;

        private ValidationResult(boolean valid, String errorKey) {
            this.valid = valid;
            this.errorKey = errorKey;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult error(String translationKey) {
            return new ValidationResult(false, translationKey);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorKey() {
            return errorKey;
        }

        public Component getErrorMessage() {
            return errorKey != null ? Component.translatable(errorKey) : Component.empty();
        }
    }
}