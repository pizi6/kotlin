// "Create class 'Foo'" "true"
// ERROR: Unresolved reference: Foo

class A<T> internal constructor(val b: B<T>) {
    internal fun test() = B.Foo<String>(2, "2")
}