package com.hunterbuddy.modules.stash.ui;

import com.hunterbuddy.modules.stash.StashLedger;
import com.hunterbuddy.modules.stash.StashPrivacySettings;
import com.hunterbuddy.modules.stash.TracerBoard;

public record StashServices(StashLedger ledger, TracerBoard tracers, StashPrivacySettings privacy) {
}
