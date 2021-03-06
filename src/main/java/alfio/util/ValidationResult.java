/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.util;

import lombok.Getter;
import org.springframework.validation.FieldError;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

@Getter
public final class ValidationResult {

    private static final ValidationResult SUCCESS = new ValidationResult(Collections.<ValidationError>emptyList());

    private final List<ValidationError> validationErrors;
    private final int errorCount;

    private ValidationResult(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
        this.errorCount = validationErrors.size();
    }

    public static ValidationResult success() {
        return SUCCESS;
    }

    public static ValidationResult failed(List<ValidationError> errors) {
        return new ValidationResult(errors);
    }

    public static ValidationResult failed(ValidationError... errors) {
        return failed(Arrays.asList(errors));
    }

    public ValidationResult ifSuccess(Operation operation) {
        if(errorCount == 0) {
            operation.doIt();
        }
        return this;
    }

    @Getter
    public static final class ValidationError {
        private final String fieldName;
        private final String message;

        public ValidationError(String fieldName, String message) {
            this.fieldName = fieldName;
            this.message = message;
        }

        public static ValidationError fromFieldError(FieldError fieldError) {
            return new ValidationError(fieldError.getField(), fieldError.getCode());
        }
    }

    @FunctionalInterface
    public static interface Operation {
        void doIt();
    }
}

