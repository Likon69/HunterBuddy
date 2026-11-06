package com.hunterbuddy.pet;

public final class PetSfxCues {
    private final PetSfx sfx;
    private PetMotion.Travel travel;
    private PetState state;
    private boolean opened;
    private boolean seated;
    private boolean lying;
    private boolean frightened;
    private boolean sparkling;
    private float armVelocity;
    public boolean beerOpened;

    public PetSfxCues(PetSfx sfx) {
        this.sfx = sfx;
    }

    public void reset() {
        travel = null;
        state = null;
        opened = seated = lying = frightened = sparkling = beerOpened = false;
        armVelocity = 0;
    }

    public void update(PetBrain brain, PetMotion motion, PetPose puppet, PetFullPose full) {
        beerOpened = false;
        if (travel != motion.travel) {
            switch (motion.travel) {
                case JUMP -> sfx.play(PetSfxAction.JUMP);
                case FALL -> sfx.play(PetSfxAction.FALL);
                case GROUNDED -> { if (travel != null) sfx.play(PetSfxAction.LAND); }
                case CLIMB -> { }
            }
        }
        if (puppet.turned) sfx.play(PetSfxAction.TURN);
        boolean scared = brain.danger() || brain.state == PetState.PANIC || brain.state == PetState.HIDE;
        if (scared && !frightened) sfx.play(PetSfxAction.SCARED);
        frightened = scared;
        if (state != brain.state) {
            opened = seated = lying = false;
            if (brain.state == PetState.RISE) sfx.play(PetSfxAction.WAKE);
        }
        if (brain.state == PetState.DRINK && !brain.preparingPose && brain.age >= brain.duration * 0.12f && !opened) {
            opened = beerOpened = true;
        }
        if (!seated && (brain.state == PetState.SIT && motion.atEdge && (puppet.seated >= 0.7f || full.ready("sit"))
            || brain.state == PetState.SETTLE && puppet.fold >= 0.8f)) {
            sfx.play(PetSfxAction.SIT);
            seated = true;
        }
        if (!lying && brain.state == PetState.LIE_DOWN && puppet.recline >= 0.05f) {
            sfx.play(PetSfxAction.LIE_DOWN);
            lying = true;
        }
        boolean crossed = puppet.angles[4] * puppet.previousAngles[4] < 0;
        float velocity = puppet.angles[2] - puppet.previousAngles[2];
        if (motion.travel == PetMotion.Travel.CLIMB && motion.wallAngle() != 0 && velocity * armVelocity < 0)
            sfx.play(PetSfxAction.CLIMB);
        if (motion.travel == PetMotion.Travel.GROUNDED && !brain.preparingPose) {
            if (brain.state == PetState.DANCE) {
                if (full.blend > 0 ? full.mix >= 0.5f && full.previousMix < 0.5f : crossed)
                    sfx.play(PetSfxAction.DANCE_STEP);
            } else if (crossed && full.blend == 0 && Math.abs(motion.x - motion.previousX) > 0.01f
                && (brain.state == PetState.WALK || brain.state == PetState.STAGGER || brain.state == PetState.HIDE))
                sfx.play(PetSfxAction.STEP);
        }
        boolean shine = full.blend > 0 && full.current != null && full.current.name().equals("moon")
            && Math.sin(full.age * 4) > 0.65;
        if (shine && !sparkling) sfx.play(PetSfxAction.SHINE);
        sparkling = shine;
        armVelocity = velocity;
        travel = motion.travel;
        state = brain.state;
    }
}
