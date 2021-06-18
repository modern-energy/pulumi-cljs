# pulumi-cljs

This is a small wrapper library making it easier to use ClojureScript
to write Infrastructure As Code via [Pulumi's](https://pulumi.com)
NodeJS API.

It also includes a variety of utilities and patterns for working with
AWS resources in Pulumi.

## Environment

This library assumes the use of
[shadow-cljs](https://github.com/thheller/shadow-cljs) to compile
ClojureScript to JavaScript, prior to running Pulumi. In this model,
Pulumi remains entirely unaware of ClojureScript and without any
explicit integration - it operates only on the complied JavaScript
output.

It is reccomended to read the documentation for both Pulumi and
shadow-cljs to understand how to install them, and they work
independently before using this tool.

A project directory using this library to deploy a Pulumi stack will
typically have the following elements:

1. A `shadow-cljs` configuration with `shadow-cljs.edn` and `deps.edn`.
2. A Pulumi NodeJS configuration with `Pulumi.yaml`, `Pulumi.<stack>.yaml`, `package.json`, `package-lock.json`, etc.
3. A shim JavaScript file. This script serves as the entry point for
   the Pulumi application, and invokes the compiled ClojureScript via
   JS->CLJS interop.

An example of such a project configuration is included in the
`examples/hello-world` subdirectory.

## Running Tests

To run tests in this project, run `shadow-cljs compile node-test && node generated/tests.js`.

## Library Features

This library consists primarily of helper functions to use Pulumi's
Node.js APIs fluently from ClojureScript.

Please see the docstrings in the code (or eventually, generated API)
docs for descriptions and documentation on the available functions.

This code extends the `ILookup` protocol to Pulumi Resource objects,
meaning that keyword lookups work as expected on Pulumi objects.

Other functions of note include:

- `resource` - to create a Pulumi resource
- `cfg` and `cfg-obj` - to efficiently retrieve values from the Pulumi
  config without needing to pass it around.
- `id` - to generate unique Pulumi resource IDs that include the project name.
- `all` - a macro with bindings as per let, which takes the inputs as
  Pulumi Outputs, and immediately returns a Pulumi Output. The value
  of the output is returned by the body of the macro.
- `json` - Given a ClojureScript datastructure, recursively convert it
  to a Pulumi Output containing a JSON string, resolving any nested
  Outputs along the way.
- `prepare-output` - Walks a Clojure data structure, converting it to
a JavaScript object so it can be returned as stack outputs.


## Usage

See the `examples/hello-world` for a simple project, or the example
[Wordpress Deployment](https://github.com/modern-energy/wordpress-pulumi-cljs)
for a more complex stack.

