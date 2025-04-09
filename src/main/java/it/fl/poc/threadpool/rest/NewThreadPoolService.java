package it.fl.poc.threadpool.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/newthreadpool")
public class NewThreadPoolService {
    final static Random rand = new Random();

    @Resource
    private ManagedExecutorService service;

    Runnable runnableTask = () -> {
        try {
            System.out.println("START runnableTask: " + Thread.currentThread().getName());
            int wait = 2000 + rand.nextInt(2000);
            TimeUnit.MILLISECONDS.sleep(wait);
            System.out.println("END runnableTask: " + Thread.currentThread().getName());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    };

    Callable<String> callableTask = () -> {
        String n = Thread.currentThread().getName();
        System.out.println("START callableTask: " + n);
        int wait = 2000 + rand.nextInt(2000);
        TimeUnit.MILLISECONDS.sleep(wait);
        System.out.println("END callableTask: " + n);
        return "Task " + n + " execution finished.";
    };

    @GET
    @Path("/runnableTasks")
    @Produces(MediaType.TEXT_PLAIN)
    public String runnableTasks(
            @DefaultValue("10") @QueryParam("size") int size,
            @DefaultValue("5") @QueryParam("tasks") int tasks) {
        ExecutorService es = Executors.newFixedThreadPool(size);
        String esName = Integer.toHexString(es.hashCode()) + " runnableTasks";
        System.out.println(esName + " - START ExecutorService: " + es.toString());
        for (int i = 0; i < tasks; i++) {
            es.execute(runnableTask);
        }
        System.out.println(esName + " - END ExecutorService: " + es.toString());
        return es.toString();
    }

    @GET
    @Path("/callableTasks")
    @Produces(MediaType.TEXT_PLAIN)
    public String callableTasks(
            @DefaultValue("10") @QueryParam("size") int size,
            @DefaultValue("5") @QueryParam("tasks") int tasks) {
        ExecutorService es = Executors.newFixedThreadPool(size);
        String esName = Integer.toHexString(es.hashCode()) + " callableTasks";
        System.out.println(esName + " - START ExecutorService: " + es.toString());
        List<Callable<String>> callableTasks = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            callableTasks.add(callableTask);
        }
        try {
            List<Future<String>> futures = es.invokeAll(callableTasks);
            for (Future<String> f : futures) {
                System.out.println(esName + " - " + f.toString());
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(esName + " - END ExecutorService: " + es.toString());
        return es.toString();
    }

    @GET
    @Path("/completableFuture")
    @Produces(MediaType.TEXT_PLAIN)
    public String completableFuture(@DefaultValue("5") @QueryParam("tasks") int tasks) {
        System.out.println("xxxxxxxx - completableFuture.supplyAsync: START.");
        System.out.println("xxxxxxxx - completableFuture.supplyAsync: ForkJoinPoll.getCommonPoolParallelism(): " +  ForkJoinPool.getCommonPoolParallelism());
        List<CompletableFuture<String>> futures = IntStream.range(1, tasks).mapToObj(
                task -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String n = Thread.currentThread().getName();
                        System.out.println("START callable: " + n);
                        int wait = 2000 + rand.nextInt(5000);
                        TimeUnit.MILLISECONDS.sleep(wait);
                        System.out.println("END callable: " + n);
                        return "Callable " + n + " execution finished after " + wait + "ms .";
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                })).collect(Collectors.toList());
        System.out.println("xxxxxxxx - completableFuturesupplyAsync: " + futures.toString());
        Stream<String> result = futures.stream().map(future -> {
            try {
                return future.join();
            } catch (Exception e) {
                throw e;
            }
        });
        System.out.println("xxxxxxxx - completableFuturesupplyAsync: " + result);
        System.out.println("xxxxxxxx - completableFuturesupplyAsync: END.");
        return result.collect(Collectors.joining(", "));
    }

    @GET
    @Path("/managedExecutor")
    @Produces(MediaType.TEXT_PLAIN)
    public String managedExecutor(
            @DefaultValue("5") @QueryParam("tasks") int tasks) {
        if (service == null) {
            // [jakarta ee - @Resource injection not happening in JAX-RS - Stack Overflow](https://stackoverflow.com/questions/55248741/resource-injection-not-happening-in-jax-rs)
            // This happens when not using CDI but only JAX-RS. 
            // To enable CDI, use @ApplicationScoped annotation.
            System.out.println("ManagedExecutor injection failed: " + service + ". Doing context lookup.");
            InitialContext ctx = null;
            try {
                ctx = new InitialContext();
                service = (ManagedExecutorService) ctx.lookup("java:comp/DefaultManagedExecutorService");
            } catch (NamingException ex) {
                throw new RuntimeException(ex);
            }
        }
        String esName = Integer.toHexString(service.hashCode()) + " runnableTasks";
        System.out.println(esName + " - START ManagedExecutor: " + service.toString());
        for (int i = 0; i < tasks; i++) {
            service.execute(runnableTask);
        }
        System.out.println(esName + " - END ManagedExecutor: " + service.toString());
        return service.toString();
    }

}
