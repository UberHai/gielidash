package com.gielidash;

import com.gielidash.api.ApiClient;
import com.gielidash.api.ApiException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;

/**
 * Gathers the local player's identity + self-reported vetting profile on the
 * client thread, then registers with the backend off-thread.
 */
@Slf4j
@Singleton
public class SessionService
{
	private final Client client;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final ApiClient api;

	@Inject
	private SessionService(Client client, ClientThread clientThread,
		ScheduledExecutorService executor, ApiClient api)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.executor = executor;
		this.api = api;
	}

	/** Call after LOGGED_IN. Reads game state on the client thread, POSTs off it. */
	public void register()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN
				|| client.getLocalPlayer() == null
				|| client.getLocalPlayer().getName() == null
				|| client.getAccountHash() == -1)
			{
				log.debug("GieliDash registration deferred - player identity not ready yet");
				return;
			}

			long accountHash = client.getAccountHash();
			String displayName = client.getLocalPlayer().getName();

			Map<String, Object> vetting = new HashMap<>();
			vetting.put("combatLevel", client.getLocalPlayer().getCombatLevel());
			vetting.put("totalLevel", client.getTotalLevel());
			vetting.put("questPoints", client.getVarpValue(VarPlayerID.QP));

			Map<String, Boolean> unlocks = new HashMap<>();
			unlocks.put("fairyRings", isFinished(Quest.FAIRYTALE_II__CURE_A_QUEEN));
			unlocks.put("varlamore", isFinished(Quest.CHILDREN_OF_THE_SUN));
			unlocks.put("morytania", isFinished(Quest.PRIEST_IN_PERIL));
			unlocks.put("spiritTrees", isFinished(Quest.TREE_GNOME_VILLAGE));
			unlocks.put("gnomeGliders", isFinished(Quest.THE_GRAND_TREE));
			unlocks.put("lunarSpells", isFinished(Quest.LUNAR_DIPLOMACY));
			vetting.put("unlocks", unlocks);

			executor.execute(() ->
			{
				try
				{
					api.hello(accountHash, displayName, vetting);
					log.debug("Registered with GieliDash server as {}", displayName);
				}
				catch (ApiException e)
				{
					log.warn("GieliDash registration failed: {}", e.getMessage());
				}
			});
		});
	}

	private boolean isFinished(Quest quest)
	{
		return quest.getState(client) == QuestState.FINISHED;
	}
}
