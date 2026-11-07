package com.hunterbuddy.pet;

public final class PetSfxCues {
    private final PetSfx sfx;
    private PetMotion.Travel travel;
    private PetState state;
    private boolean opened;
    private boolean seated;
    private boolean lying;
    private boolean frightened;
    public boolean beerOpened;

    public PetSfxCues(PetSfx sfx) {
        this.sfx = sfx;
    }

    public void reset() {
        travel = null;
        state = null;
        opened = seated = lying = frightened = beerOpened = false;
    }

    public void update(PetBrain brain, PetMotion motion, PetPose puppet, PetFullPose full) {
        beerOpened = false;
        if (travel != motion.travel) {
            switch (motion.travel) {
                case JUMP -> sfx.play(PetSfxAction.JUMP);
                case FALL -> sfx.play(PetSfxAction.FALL);
                case GROUNDED -> { if (travel != null) sfx.play(PetSfxAction.LAND); }
            }
        }
        if (puppet.turned && (brain.state == PetState.TURN || brain.preparingPose)) sfx.play(PetSfxAction.TURN);
        boolean scared = brain.danger() || brain.state == PetState.PANIC || brain.state == PetState.HIDE;
        if (scared && !frightened) sfx.play(PetSfxAction.SCARED);
        frightened = scared;
        if (state != brain.state) {
            opened = seated = lying = false;
            if (brain.state == PetState.RISE) sfx.play(PetSfxAction.WAKE);
        }
        if (brain.drinkProgress() < 0.05f) opened = false;
        if ((brain.state == PetState.DRINK || brain.sitDrink) && !brain.preparingPose && brain.drinkProgress() >= 0.12f && !opened) {
            opened = beerOpened = true;
        }
        if (!seated && (brain.state == PetState.SIT && motion.atEdge && (puppet.seated >= 0.7f || full.ready("sit"))
            || brain.state == PetState.SETTLE && puppet.crouch >= 0.3f)) {
            sfx.play(PetSfxAction.SIT);
            seated = true;
        }
        if (!lying && brain.state == PetState.LIE_DOWN) {
            sfx.play(PetSfxAction.LIE_DOWN);
            lying = true;
        }
        boolean crossed = puppet.angles[4] * puppet.previousAngles[4] < 0;
        if (motion.travel == PetMotion.Travel.GROUNDED && !brain.preparingPose) {
            if (crossed && full.blend == 0 && Math.abs(motion.x - motion.previousX) > 0.01f
                && (brain.state == PetState.WALK || brain.state == PetState.STAGGER || brain.state == PetState.HIDE))
                sfx.play(PetSfxAction.STEP);
        }
        travel = motion.travel;
        state = brain.state;
    }
}
