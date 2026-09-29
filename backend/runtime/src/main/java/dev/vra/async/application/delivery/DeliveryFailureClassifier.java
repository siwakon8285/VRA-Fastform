package dev.vra.async.application.delivery;

import java.util.Objects;

/** Maps explicit evidence to stable database codes; an arbitrary exception is not retry evidence. */
public final class DeliveryFailureClassifier {
    public enum FailureClass {
        RETRYABLE_TRANSIENT, NON_RETRYABLE, UNKNOWN_OUTCOME, POISON, OPERATOR_REQUIRED
    }

    public enum Evidence {
        ROLLED_BACK_CONSUMER_TRANSIENT,
        UNSUPPORTED_EVENT_CONTRACT,
        NON_RETRYABLE_VALIDATION,
        OPERATOR_REVIEW_REQUIRED,
        UNPROVEN_CAUGHT_FAILURE,
        EXTERNAL_OUTCOME_UNCERTAIN
    }

    public record Classification(FailureClass failureClass, String reasonCode) {
        public Classification {
            Objects.requireNonNull(failureClass, "failureClass");
            Objects.requireNonNull(reasonCode, "reasonCode");
        }
    }

    public Classification classify(Evidence evidence) {
        return switch (Objects.requireNonNull(evidence, "evidence")) {
            case ROLLED_BACK_CONSUMER_TRANSIENT ->
                    new Classification(FailureClass.RETRYABLE_TRANSIENT, "RETRYABLE_TRANSIENT");
            case UNSUPPORTED_EVENT_CONTRACT ->
                    new Classification(FailureClass.POISON, "UNSUPPORTED_EVENT_CONTRACT");
            case NON_RETRYABLE_VALIDATION ->
                    new Classification(FailureClass.NON_RETRYABLE, "NON_RETRYABLE");
            case OPERATOR_REVIEW_REQUIRED, UNPROVEN_CAUGHT_FAILURE ->
                    new Classification(FailureClass.OPERATOR_REQUIRED, "OPERATOR_REQUIRED");
            case EXTERNAL_OUTCOME_UNCERTAIN ->
                    new Classification(FailureClass.UNKNOWN_OUTCOME, "UNKNOWN_EXTERNAL_RESULT");
        };
    }
}
