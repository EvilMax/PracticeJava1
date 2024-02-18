package org.scala.practice;

import org.scala.practice.domain.ApplicationStatusResponse;
import org.scala.practice.domain.Handler;
import org.scala.practice.impl.ClientImpl;
import org.scala.practice.impl.HandlerImpl;

public class Main {
    public static void main(String[] args) {
        Handler handler = new HandlerImpl(new ClientImpl());
        ApplicationStatusResponse response = handler.performOperation("12345");
        if (response instanceof ApplicationStatusResponse.Failure failureResponse) {
            System.out.println("Failure!");
            System.out.println("Retries count: " + failureResponse.retriesCount());
            assert failureResponse.lastRequestTime() != null;
            System.out.println("Last failed time duration: " + failureResponse.lastRequestTime().getSeconds() + " s");
        } else if (response instanceof ApplicationStatusResponse.Success successResponse) {
            System.out.println("Success!");
            System.out.println("Status: " + successResponse.status());
            System.out.println("AppId: " + successResponse.id());
        }
    }
}