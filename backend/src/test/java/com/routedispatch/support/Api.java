package com.routedispatch.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Thin MockMvc helper: real login, bearer tokens, JSON bodies and polling for asynchronous results. */
@TestComponent
public class Api {

    private final MockMvc mvc;
    private final ObjectMapper json;

    public Api(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public String login(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", TestFleet.PASSWORD));
        String response = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("token").asText();
    }

    public ResultActions get(String token, String path) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.get(path), token));
    }

    public ResultActions post(String token, String path, Object body) throws Exception {
        MockHttpServletRequestBuilder request = auth(MockMvcRequestBuilders.post(path), token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    public JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Polls GET {@code path} until the JSON satisfies the condition or the timeout passes. */
    public JsonNode awaitJson(String token, String path, Predicate<JsonNode> condition, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = read(get(token, path).andExpect(status().isOk()));
            if (condition.test(last)) {
                return last;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Timed out waiting on " + path + "; last response: " + last);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }
}
