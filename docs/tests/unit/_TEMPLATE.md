# Test Evidence: <short behavior name>

- **Test type:** Unit
- **Requirement IDs:** `<ID>`
- **Scenario IDs:** `<ID>`
- **Test class/method:** `<fully qualified class and method>`
- **Implementation commit:** `<short SHA or pending>`

## Protected behavior

<Externally observable rule and failure mode protected by this test.>

## Test method

<Setup, action, and assertions. Explain why this is the narrowest production-shaped test.>

## Hand-derived expected result

<Expected values or state derived independently of the implementation.>

## RED

**Command**

```text
<exact command>
```

**Observed result**

```text
<relevant failing output and why it failed for the expected missing behavior>
```

## GREEN

**Command**

```text
<exact command>
```

**Observed result**

```text
<relevant passing output>
```

## Affected suite

**Command and result**

```text
<exact broader command and result>
```

## External-test boundaries

<What this test deliberately does not prove, including infrastructure or browser boundaries.>
