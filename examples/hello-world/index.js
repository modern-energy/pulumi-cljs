"use strict";

// Import ClojureScript code from compilation directory
const stack = require("./generated/stack.js");

// Invoke the stack function
const outputs = stack();

// Use the stack functions return value as module exports, to make
// them Pulumi Stack Outputs.
Object.assign(exports, outputs);
