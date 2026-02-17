package cluster.java;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.Member;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

public class ClusterInfoActor extends AbstractActor {

    public record GetClusterInfo() implements Serializable {
    }

    public record ClusterMemberDto(String address, String status, List<String> roles, int upNumber) implements Serializable {
    }

    public record ClusterInfoResponse(String selfAddress, String leader, int memberCount, List<ClusterMemberDto> members)
        implements Serializable {
    }

    public static Props props() {
        return Props.create(ClusterInfoActor.class, ClusterInfoActor::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(GetClusterInfo.class, msg -> {
                Cluster cluster = Cluster.get(getContext().getSystem());
                List<ClusterMemberDto> members = StreamSupport.stream(cluster.state().getMembers().spliterator(), false)
                    .sorted(Comparator.comparing(member -> member.address().toString()))
                    .map(this::toDto)
                    .toList();

                var leader = cluster.state().getLeader();

                getSender().tell(
                    new ClusterInfoResponse(
                        cluster.selfAddress().toString(),
                        leader != null ? leader.toString() : "",
                        members.size(),
                        members),
                    getSelf());
            })
            .build();
    }

    private ClusterMemberDto toDto(Member member) {
        return new ClusterMemberDto(
            member.address().toString(),
            member.status().toString(),
            member.getRoles().stream().sorted().toList(),
            member.upNumber());
    }
}
