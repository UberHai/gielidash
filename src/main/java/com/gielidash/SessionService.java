package com.gielidash;

import com.gielidash.api.ApiClient;
import com.gielidash.api.ApiException;
import java.util.HashMap;
import java.util.Map;
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
	private static final long REFRESH_INTERVAL_MS = 10 * 60 * 1000L;

	private final Client client;
	private final ClientThread clientThread;
	private final ApiClient api;

	/** The plugin's own API thread - never the shared client executor. */
	private volatile java.util.concurrent.Executor executor;

	private volatile long lastHelloAt;

	@Inject
	private SessionService(Client client, ClientThread clientThread, ApiClient api)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.api = api;
	}

	/** Wired by the plugin in startUp, before any register() can fire. */
	void setExecutor(java.util.concurrent.Executor executor)
	{
		this.executor = executor;
	}

	/** Call after LOGGED_IN. Reads game state on the client thread, POSTs off it. */
	public void register()
	{
		// Debounce: region loads refire LOGGED_IN constantly; once registered,
		// a vetting refresh every 10 minutes is plenty
		if (api.hasToken() && System.currentTimeMillis() - lastHelloAt < REFRESH_INTERVAL_MS)
		{
			return;
		}

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

			java.util.concurrent.Executor apiThread = executor;
			if (apiThread == null)
			{
				return;
			}
			apiThread.execute(() ->
			{
				try
				{
					api.hello(accountHash, displayName, vetting);
					lastHelloAt = System.currentTimeMillis();
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
