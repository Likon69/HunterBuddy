/*
 * Port of com.lambda.interaction.managers.ActionInfo (lambda 1.21.11).
 */
package com.hunterbuddy.lambda;

import java.util.Collection;

public interface ActionInfo {
    BuildContext getContext();
    Collection<BuildContext> getPendingInteractionsList();
}
