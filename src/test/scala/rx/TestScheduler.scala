package rx

import akka.actor.ActorSystem

class TestScheduler extends AkkaScheduler(ActorSystem())
