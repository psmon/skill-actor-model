using Akka.Actor;
using Akka.Cluster;

namespace ClusterActors;

public sealed class ClusterInfoActor : ReceiveActor
{
    public sealed record GetClusterInfo;
    public sealed record ClusterMemberDto(string Address, string Status, IReadOnlyList<string> Roles);
    public sealed record ClusterInfoResponse(string SelfAddress, string Leader, int MemberCount, IReadOnlyList<ClusterMemberDto> Members);

    private readonly Cluster _cluster = Cluster.Get(Context.System);

    public ClusterInfoActor()
    {
        Receive<GetClusterInfo>(msg =>
        {
            var members = _cluster.State.Members
                .OrderBy(m => m.Address.ToString())
                .Select(m => new ClusterMemberDto(
                    m.Address.ToString(),
                    m.Status.ToString(),
                    m.Roles.ToArray()))
                .ToArray();

            var response = new ClusterInfoResponse(
                _cluster.SelfAddress.ToString(),
                _cluster.State.Leader?.ToString() ?? string.Empty,
                members.Length,
                members);

            Sender.Tell(response);
        });
    }
}
