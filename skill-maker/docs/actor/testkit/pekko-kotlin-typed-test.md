# Kotlin + Pekko Typed TestKit 가이드

Kotlin + Apache Pekko Typed(1.1.x) 액터를 테스트할 때는 `ActorTestKit` + `TestProbe` 조합을 기본으로 사용합니다.

## 1. 의존성

```kotlin
val pekkoVersion = "1.1.3"

dependencies {
    implementation("org.apache.pekko:pekko-actor-typed_2.13:$pekkoVersion")
    testImplementation("org.apache.pekko:pekko-actor-testkit-typed_2.13:$pekkoVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}
```

## 2. Hello -> World 샘플

### 액터 프로토콜

```kotlin
sealed interface ACommand
data class Start(val target: ActorRef<BCommand>, val reportTo: ActorRef<String>) : ACommand
data class World(val message: String) : ACommand

data class Hello(val replyTo: ActorRef<ACommand>)
typealias BCommand = Hello
```

### ActorA

```kotlin
class ActorA private constructor(context: ActorContext<ACommand>) : AbstractBehavior<ACommand>(context) {
    companion object {
        fun create(): Behavior<ACommand> = Behaviors.setup(::ActorA)
    }

    private var reportTo: ActorRef<String>? = null

    override fun createReceive(): Receive<ACommand> =
        newReceiveBuilder()
            .onMessage(Start::class.java) {
                reportTo = it.reportTo
                it.target.tell(Hello(context.self))
                Behaviors.same()
            }
            .onMessage(World::class.java) {
                reportTo?.tell(it.message)
                Behaviors.same()
            }
            .build()
}
```

### ActorB

```kotlin
class ActorB private constructor(context: ActorContext<BCommand>) : AbstractBehavior<BCommand>(context) {
    companion object {
        fun create(): Behavior<BCommand> = Behaviors.setup(::ActorB)
    }

    override fun createReceive(): Receive<BCommand> =
        newReceiveBuilder()
            .onMessage(Hello::class.java) {
                it.replyTo.tell(World("World"))
                Behaviors.same()
            }
            .build()
}
```

### 테스트 코드

```kotlin
class HelloWorldActorTest {
    private lateinit var testKit: ActorTestKit

    @BeforeEach
    fun setup() {
        testKit = ActorTestKit.create()
    }

    @AfterEach
    fun teardown() {
        testKit.shutdownTestKit()
    }

    @Test
    fun `A sends Hello to B and receives World`() {
        val a = testKit.spawn(ActorA.create(), "a")
        val b = testKit.spawn(ActorB.create(), "b")
        val probe = testKit.createTestProbe<String>()

        a.tell(Start(b, probe.ref()))

        probe.expectMessage("World")
        probe.expectNoMessage()
    }
}
```

## 3. 권장 검증 포인트

- 메시지 내용: `expectMessage(expected)`
- 메시지 없음: `expectNoMessage()`
- 종료 검증: `testKit.stop(actorRef)`
- 타입 안전: 메시지 계층(sealed class/interface) + `replyTo` 명시

## 4. 자주 하는 실수

- `Thread.sleep`으로 타이밍을 맞춤: 비결정적 테스트가 됩니다.
- 테스트 종료 시 `shutdownTestKit()` 누락: 리소스 누수로 후속 테스트 실패 가능.
- 프로토콜에 `replyTo` 없이 전역 참조 사용: 테스트/재사용성 저하.
