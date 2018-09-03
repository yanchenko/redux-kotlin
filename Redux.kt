interface Action

typealias Reducer<T> = (state: T, action: Action) -> T

typealias Subscriber<T> = (state: T) -> Unit

typealias Middleware<T> = (state: T, action: Action, nextDispatcher: Dispatcher) -> Unit
typealias Dispatcher = (action: Action) -> Unit

interface Subscription {
	fun cancel(): Boolean
}

operator fun <T> Reducer<T>.plus(other: Reducer<T>) =
		{ state: T, action: Action -> other(this(state, action), action) }

class Store<out T>(
		@Volatile private var state: T,
		private val reducer: Reducer<T>,
		vararg middlewares: Middleware<T>
) {

	private val dispatchers: List<Dispatcher>
	private val subscribers = mutableListOf<Subscriber<T>>()

	init {
		val list = mutableListOf<Dispatcher>()
		list.add({ action -> reduceAndNotify(action) })
		for (i in (middlewares.size - 1) downTo 0) {
			val middleware = middlewares[i]
			val nextDispatcher = list[0]
			val dispatcher = { action: Action ->
				middleware(state, action, nextDispatcher)

			}
			list.add(0, dispatcher)
		}
		dispatchers = list
	}

	fun subscribe(subscriber: Subscriber<T>): Subscription {
		subscribers.add(subscriber)
		return object : Subscription {
			override fun cancel() =
					subscribers.remove(subscriber)
		}
	}

	@Synchronized
	fun dispatch(action: Action): T {
		dispatchers[0](action)
		return state
	}

	fun state() = state

	private fun reduceAndNotify(action: Action) {
		state = reducer(state, action)
		subscribers.forEach { it(state) }
	}

}
