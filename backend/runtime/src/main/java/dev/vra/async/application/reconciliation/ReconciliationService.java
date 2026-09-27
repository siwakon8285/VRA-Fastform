package dev.vra.async.application.reconciliation;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import dev.vra.async.application.external.ExternalEffectPort;
import dev.vra.async.application.external.ExternalEffectPort.Observation;
import dev.vra.async.application.reconciliation.ReconciliationPort.Claim;

/** Claims commit before observation; each finalization is a separate guarded call. */
public final class ReconciliationService {
    private final ReconciliationPort port;
    private final ExternalEffectPort external;
    private final ReconciliationPolicy policy;

    public ReconciliationService(ReconciliationPort port, ExternalEffectPort external,
            ReconciliationPolicy policy) {
        this.port = Objects.requireNonNull(port, "port");
        this.external = Objects.requireNonNull(external, "external");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** The port's claim transaction has ended before this method invokes observe. */
    public int reconcileAvailable(String workerRef, int batchSize, Duration lease) {
        List<Claim> claims = port.claim(workerRef, batchSize, lease);
        for (Claim claim : claims) {
            Observation observation = observeSafely(claim);
            switch (policy.decide(observation)) {
                case CONFIRM_SUCCESS -> port.confirmSuccess(claim.caseId(), claim.claimToken());
                case CONFIRM_NO_EFFECT -> {
                    var cycle = port.readDeliveryCycle(claim.eventId());
                    var retryDelay = policy.noEffectRetryDelay(cycle);
                    // The simulator proves strong absence for this same eventId. V3 decides budget.
                    port.confirmNoEffect(claim.caseId(), claim.claimToken(), retryDelay.isPresent(),
                            retryDelay.orElse(Duration.ofMillis(1)));
                }
                case WAIT_INDETERMINATE -> port.waitIndeterminate(claim.caseId(), claim.claimToken(),
                        policy.nextQueryDelay(claim).orElse(Duration.ofMillis(1)));
            }
        }
        return claims.size();
    }

    private Observation observeSafely(Claim claim) {
        try {
            return Objects.requireNonNull(external.observe(claim.eventId()), "observation");
        } catch (RuntimeException uncertain) {
            return Observation.INDETERMINATE;
        }
    }
}
