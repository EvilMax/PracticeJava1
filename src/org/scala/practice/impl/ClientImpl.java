package org.scala.practice.impl;

import org.scala.practice.domain.Client;
import org.scala.practice.domain.Response;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeoutException;

public class ClientImpl implements Client {
    private static final int MAX_DELAY = 10;

    private Response getRandomResponse(String id) {
        Random rnd = new Random();
        int status = rnd.nextInt(3);
        return switch (status) {
            case 0 -> new Response.Success("Some status", String.valueOf(id));
            case 1 -> new Response.RetryAfter(Duration.ofSeconds(rnd.nextInt(MAX_DELAY)));
            default -> new Response.Failure(new TimeoutException());
        };
    }

    @Override
    public Response getApplicationStatus1(String id) {
        return getRandomResponse(id);
    }

    @Override
    public Response getApplicationStatus2(String id) {
        return getRandomResponse(id);
    }
}
