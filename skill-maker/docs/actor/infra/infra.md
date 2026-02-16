# 액터 시스템 분산 클러스터 인프라 가이드

분산 액터 시스템의 서비스 디스커버리 및 인프라 구성 문서입니다.
각 플랫폼별로 **Docker Compose(Type A)**와 **Kubernetes(Type B)** 두 가지 디스커버리 방식을 다룹니다.

## 플랫폼별 인프라 문서

| 플랫폼 | 문서 | 프레임워크 버전 |
|--------|------|---------------|
| Kotlin + Pekko Typed | [infra-kotlin-pekko-typed.md](./infra-kotlin-pekko-typed.md) | Pekko 1.1.x + Management 1.2.0 |
| Java + Akka Classic | [infra-java-akka-classic.md](./infra-java-akka-classic.md) | Akka 2.7.x + Management 1.2.0 |
| C# + Akka.NET | [infra-dotnet-akka-net.md](./infra-dotnet-akka-net.md) | Akka.NET 1.5.x + Management 1.5.59 |

## 참고 링크

### C# Akka.NET
- https://petabridge.com/blog/akkadotnet-guide-to-kubernetes/
- https://github.com/petabridge/akkadotnet-bootstrap/tree/dev/src/Akka.Bootstrap.Docker

### Kotlin Pekko Typed
- https://pekko.apache.org/docs/pekko-management/current/discovery/index.html
- https://pekko.apache.org/docs/pekko-management/current/discovery/kubernetes.html
- https://pekko.apache.org/docs/pekko-management/current/discovery/consul.html

### Java Akka Classic
- https://doc.akka.io/libraries/akka-management/current/bootstrap/kubernetes-api.html
- https://doc.akka.io/libraries/akka-management/current/bootstrap/
