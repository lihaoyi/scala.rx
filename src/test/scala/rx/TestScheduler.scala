package rx

import akka.actor.ActorSystem
import rx.ops.AkkaScheduler

class TestScheduler extends AkkaScheduler(ActorSystem())
