package com.gielidash.api;

import com.gielidash.GieliDashConfig;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * All HTTP against the GieliDash backend. Every method here is BLOCKING and must
 * be called off the client thread (executor / @Schedule asynchronous).
 */
@Slf4j
@Singleton
public class ApiClient
{
	private static final String DEFAULT_BASE_URL = "https://runelite.solvyr.tech/api/v1";
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final String USER_AGENT =
		"GieliDash/0.1.0 RuneLite/" + RuneLiteProperties.getVersion();
	private static final String TOKEN_KEY = "authToken";

	private final OkHttpClient http;
	private final Gson gson;
	private final GieliDashConfig config;
	private final ConfigManager configManager;

	@Inject
	private ApiClient(OkHttpClient http, Gson gson, GieliDashConfig config, ConfigManager configManager)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
		this.configManager = configManager;
	}

	public boolean hasToken()
	{
		String token = getToken();
		return token != null && !token.isEmpty();
	}

	/**
	 * Registers/refreshes this account; stores the minted token on first contact.
	 * Sends our token when we have one - the server requires it for updates to
	 * an existing account (anti-spoofing).
	 */
	public void hello(long accountHash, String displayName, Map<String, Object> vetting)
	{
		HelloResponse resp = post("/session/hello", Map.of(
			"accountHash", accountHash,
			"displayName", displayName,
			"vetting", vetting
		), HelloResponse.class, hasToken());

		if (resp.token != null && !resp.token.isEmpty())
		{
			configManager.setConfiguration(GieliDashConfig.GROUP, TOKEN_KEY, resp.token);
			log.debug("GieliDash token minted and stored");
		}
	}

	public List<Order> getOpenOrders()
	{
		return get("/orders?status=open", OrdersResponse.class).orders;
	}

	public Order getOrder(int id)
	{
		return get("/orders/" + id, OrderResponse.class).order;
	}

	public List<Order> getMyOrders()
	{
		return get("/orders/mine", OrdersResponse.class).orders;
	}

	public void sendLocation(int orderId, int x, int y, int plane, int world)
	{
		post("/location", Map.of(
			"orderId", orderId, "x", x, "y", y, "plane", plane, "world", world
		), SimpleResponse.class, true);
	}

	public void submitRating(int orderId, int stars, @Nullable String comment)
	{
		post("/ratings", Map.of(
			"orderId", orderId, "stars", stars, "comment", comment == null ? "" : comment
		), SimpleResponse.class, true);
	}

	public List<DasherPost> getPosts()
	{
		return get("/posts", PostsResponse.class).posts;
	}

	public Metrics getMetrics()
	{
		return get("/metrics", MetricsResponse.class).metrics;
	}

	public void createPost(String message, @Nullable String feeNote)
	{
		post("/posts", Map.of(
			"message", message, "feeNote", feeNote == null ? "" : feeNote
		), SimpleResponse.class, true);
	}

	public void deactivatePost()
	{
		post("/posts/deactivate", Map.of(), SimpleResponse.class, true);
	}

	public void submitTradeReport(int orderId, String counterpartName, List<OrderItem> items,
		long gp, int world, int x, int y, int plane)
	{
		post("/trade-reports", Map.of(
			"orderId", orderId, "counterpartName", counterpartName, "items", items,
			"gp", gp, "world", world, "x", x, "y", y, "plane", plane
		), SimpleResponse.class, true);
	}

	public void acceptOrder(int id)
	{
		post("/orders/" + id + "/accept", Map.of(), SimpleResponse.class, true);
	}

	public void updateStatus(int id, String status)
	{
		post("/orders/" + id + "/status", Map.of("status", status), SimpleResponse.class, true);
	}

	public void cancelOrder(int id, String reason)
	{
		post("/orders/" + id + "/cancel", Map.of("reason", reason), SimpleResponse.class, true);
	}

	public int createOrder(List<OrderItem> items, int x, int y, int plane, int world, long feeGp,
		@Nullable String notes, @Nullable String directedToName)
	{
		CreateResponse resp = post("/orders", Map.of(
			"items", items,
			"dest", Map.of("x", x, "y", y, "plane", plane),
			"world", world,
			"feeGp", feeGp,
			"notes", notes == null ? "" : notes,
			"directedToName", directedToName == null ? "" : directedToName
		), CreateResponse.class, true);
		return resp.id;
	}

	public List<Order> getRequests()
	{
		return get("/orders/requests", OrdersResponse.class).orders;
	}

	public void declineOrder(int id)
	{
		post("/orders/" + id + "/decline", Map.of(), SimpleResponse.class, true);
	}

	private String baseUrl()
	{
		String override = config.baseUrlOverride();
		String base = override == null || override.trim().isEmpty() ? DEFAULT_BASE_URL : override.trim();
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	@Nullable
	private String getToken()
	{
		return configManager.getConfiguration(GieliDashConfig.GROUP, TOKEN_KEY);
	}

	private <T extends SimpleResponse> T get(String path, Class<T> type)
	{
		return execute(new Request.Builder().url(baseUrl() + path), type, true);
	}

	private <T extends SimpleResponse> T post(String path, Object body, Class<T> type, boolean auth)
	{
		Request.Builder builder = new Request.Builder()
			.url(baseUrl() + path)
			.post(RequestBody.create(JSON, gson.toJson(body)));
		return execute(builder, type, auth);
	}

	private <T extends SimpleResponse> T execute(Request.Builder builder, Class<T> type, boolean auth)
	{
		builder.header("User-Agent", USER_AGENT);
		if (auth)
		{
			String token = getToken();
			if (token == null || token.isEmpty())
			{
				throw new ApiException("not_registered");
			}
			builder.header("Authorization", "Bearer " + token);
		}

		try (Response response = http.newCall(builder.build()).execute())
		{
			String raw = response.body() != null ? response.body().string() : "";
			T parsed = gson.fromJson(raw, type);
			if (parsed == null)
			{
				throw new ApiException("empty_response");
			}
			if (!parsed.ok)
			{
				throw new ApiException(parsed.error != null ? parsed.error : "http_" + response.code());
			}
			return parsed;
		}
		catch (IOException e)
		{
			throw new ApiException("network_error", e);
		}
	}

	public static class SimpleResponse
	{
		boolean ok;
		@Nullable
		String error;
		@Nullable
		String status;
	}

	private static class HelloResponse extends SimpleResponse
	{
		@Nullable
		String token;
	}

	private static class OrdersResponse extends SimpleResponse
	{
		List<Order> orders;
	}

	private static class PostsResponse extends SimpleResponse
	{
		List<DasherPost> posts;
	}

	private static class MetricsResponse extends SimpleResponse
	{
		Metrics metrics;
	}

	private static class OrderResponse extends SimpleResponse
	{
		Order order;
	}

	private static class CreateResponse extends SimpleResponse
	{
		int id;
	}
}
