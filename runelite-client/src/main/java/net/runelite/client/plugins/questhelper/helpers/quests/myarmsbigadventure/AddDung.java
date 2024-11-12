package net.runelite.client.plugins.questhelper.helpers.quests.myarmsbigadventure;


import net.runelite.api.ItemID;
import net.runelite.api.NullObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.questhelper.requirements.item.ItemRequirement;
import net.runelite.client.plugins.questhelper.steps.ObjectStep;

import java.util.Arrays;

public class AddDung extends ObjectStep {
    ItemRequirement dung = new ItemRequirement("Ugthanki dung", ItemID.UGTHANKI_DUNG, 3);
    ItemRequirement spade = new ItemRequirement("Spade", ItemID.SPADE);

    public AddDung(QuestHelper questHelper) {
        super(questHelper, NullObjectID.NULL_18867, new WorldPoint(2831, 3696, 0), "Add 3 ugthanki dung on My Arm's soil patch.");
        this.addIcon(ItemID.UGTHANKI_DUNG);
        dung.setTooltip("You can get some by feeding the camels in Pollnivneach hot sauce, then using a bucket on their dung");
        dung.setHighlightInInventory(true);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        updateSteps();
    }

    protected void updateSteps() {
        int numCompToAdd = 3 - client.getVarbitValue(2791);
        dung.setQuantity(numCompToAdd);
        this.setRequirements(Arrays.asList(dung, spade));
        this.setText("Add " + numCompToAdd + " ugthanki dung on My Arm's soil patch.");
    }
}

