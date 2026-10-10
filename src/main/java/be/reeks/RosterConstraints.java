package be.reeks;

import ai.timefold.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.uni.UniConstraintStream;

/**
 * Eén constraint per regel. Alle regels delen dezelfde Snapshot (één evaluatie per zet).
 * Zachte gewichten komen uit {@link Roster#getWeights()} (de sliders).
 */
public class RosterConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory f) {
        UniConstraintStream<Snapshot> snap = f.forEach(Slot.class).groupBy(new GridCollector());
        Constraint[] out = new Constraint[Rules.H + Rules.Z];
        int n = 0;
        for (int i = 0; i < Rules.H; i++) {
            final int idx = i;
            out[n++] = snap.penalizeLong(HardSoftLongScore.ONE_HARD, s -> s.hard(idx)).asConstraint(Rules.HARD[i]);
        }
        for (int i = 0; i < Rules.Z; i++) {
            final int idx = i;
            if (i == 9) {   // Z10 is een beloning (meer vrije weekends = beter)
                out[n++] = snap.rewardLong(HardSoftLongScore.ONE_SOFT, s -> -s.soft(idx)).asConstraint(Rules.SOFT[i]);
            } else {
                out[n++] = snap.penalizeLong(HardSoftLongScore.ONE_SOFT, s -> s.soft(idx)).asConstraint(Rules.SOFT[i]);
            }
        }
        return out;
    }
}
