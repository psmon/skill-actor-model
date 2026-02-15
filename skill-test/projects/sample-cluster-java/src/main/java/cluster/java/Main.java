package cluster.java;

import akka.actor.ActorSystem;
import com.typesafe.config.ConfigFactory;

/**
 * Main entry point for the cluster application.
 */
public class Main {

    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("ClusterSystem", ConfigFactory.load());
        System.out.println("Cluster system started: " + system.name());
    }
}
