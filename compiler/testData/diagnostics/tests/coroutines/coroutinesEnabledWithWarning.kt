// !DIAGNOSTICS: -UNUSED_PARAMETER
// !LANGUAGE: +WarnOnCoroutines

<!EXPERIMENTAL_FEATURE_WARNING!>suspend<!> fun suspendHere(): String = "OK"

fun builder(c: suspend () -> Unit) {

}

fun box(): String {
    var result = ""

    <!EXPERIMENTAL_FEATURE_WARNING!>builder<!> {
        suspendHere()
    }

    return result
}
