package org.scala.practice.impl;

import org.scala.practice.domain.ApplicationStatusResponse;
import org.scala.practice.domain.Client;
import org.scala.practice.domain.Handler;
import org.scala.practice.domain.Response;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class HandlerImpl implements Handler {

    private Client client;
    private AtomicInteger retriesCount = new AtomicInteger(0);
    private AtomicLong lastRequestTime = new AtomicLong(-1);

    public HandlerImpl(Client client) {
        this.client = client;
    }

    private synchronized ApplicationStatusResponse.Failure getFailureResponse() {
        Duration duration = lastRequestTime.get() >= 0 ? Duration.ofMillis(lastRequestTime.get()) : null;
        return new ApplicationStatusResponse.Failure(duration, retriesCount.get());
    }

    interface Command {
        Response execute(String id);
    }

    class Command1 implements Command {

        @Override
        public Response execute(String id) {
            return client.getApplicationStatus1(id);
        }
    }

    class Command2 implements Command {

        @Override
        public Response execute(String id) {
            return client.getApplicationStatus2(id);
        }
    }


    class OperationTask implements Callable<ApplicationStatusResponse> {
        private String id;
        private Command command;

        public OperationTask(String id, Command command) {
            this.id = id;
            this.command = command;
        }

        @Override
        public ApplicationStatusResponse call() throws Exception {
            long startTime = System.currentTimeMillis();
            Response rawResponse =  command.execute(id);
            while (rawResponse instanceof Response.RetryAfter retryResponse) {
                retriesCount.incrementAndGet();
                try {
                    Thread.sleep(retryResponse.delay().toMillis());
                } catch (InterruptedException ex) {
                    lastRequestTime.set(System.currentTimeMillis() - startTime);
                    return getFailureResponse();
                }
                rawResponse = command.execute(id);
            }
            if (rawResponse instanceof Response.Success successResponse) {
                return new ApplicationStatusResponse.Success(successResponse.applicationStatus(), successResponse.applicationId());
            } else {
                lastRequestTime.set(System.currentTimeMillis() - startTime);
                return getFailureResponse();
            }
        }
    }

    @Override
    public ApplicationStatusResponse performOperation(String id) {
        ExecutorService single = Executors.newSingleThreadExecutor();
        Callable<ApplicationStatusResponse> callable = new Callable<ApplicationStatusResponse>() {
            @Override
            public ApplicationStatusResponse call() throws Exception {
                ExecutorService executor = Executors.newCachedThreadPool();
                CompletionService<ApplicationStatusResponse> pool = new ExecutorCompletionService<>(executor);

                OperationTask task1 = new OperationTask(id, new Command1());
                OperationTask task2 = new OperationTask(id, new Command2());

                ApplicationStatusResponse result = null;
                List<Future<ApplicationStatusResponse>> futures = new ArrayList<>(2);
                try {
                    futures.add(pool.submit(task1));
                    futures.add(pool.submit(task2));
                    for (int i = 0; i < 2; i++) {
                        System.out.println("Getting result from completion service: " + (i+1));
                        result = pool.take().get();
                        if (result instanceof ApplicationStatusResponse.Success) {
                            break;
                        } else {
                            result = getFailureResponse();
                        }
                    }
                    executor.shutdown();
                } catch (InterruptedException | ExecutionException e) {
                    return getFailureResponse();
                } finally {
                    futures.forEach(future -> future.cancel(true));
                }
                return result;
            }
        };

        Future<ApplicationStatusResponse> finalResult = single.submit(callable);
        try {
            return finalResult.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return getFailureResponse();
        } finally {
            single.shutdown();
        }
    }
}
