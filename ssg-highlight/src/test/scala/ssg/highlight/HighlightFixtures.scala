/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package highlight

object HighlightFixtures {

  val bash: String =
    """#!/bin/bash
      |# Deploy script
      |NAME="world"
      |COUNT=42
      |if [ "$COUNT" -gt 0 ]; then
      |  echo "Hello, $NAME" | tee /dev/null
      |  for i in $(seq 1 $COUNT); do
      |    printf "%d\n" "$i"
      |  done
      |fi""".stripMargin

  val c: String =
    """#include <stdio.h>
      |/* Factorial function */
      |int factorial(int n) {
      |    if (n <= 1) return 1;
      |    return n * factorial(n - 1);
      |}
      |int main(void) {
      |    int result = factorial(10);
      |    printf("Result: %d\n", result);
      |    return 0;
      |}""".stripMargin

  val cpp: String =
    """#include <iostream>
      |#include <vector>
      |// Generic accumulator
      |template <typename T>
      |T accumulate(const std::vector<T>& items) {
      |    T sum = T{};
      |    for (const auto& item : items) {
      |        sum += item;
      |    }
      |    return sum;
      |}
      |int main() {
      |    auto v = std::vector<int>{1, 2, 3};
      |    std::cout << accumulate(v) << std::endl;
      |}""".stripMargin

  val cSharp: String =
    """using System;
      |using System.Linq;
      |// Greeting service
      |namespace Demo {
      |    public class Greeter {
      |        private readonly string _name;
      |        public Greeter(string name) { _name = name; }
      |        public string Greet() => $"Hello, {_name}!";
      |    }
      |    class Program {
      |        static void Main(string[] args) {
      |            var g = new Greeter("World");
      |            Console.WriteLine(g.Greet());
      |            int[] nums = { 1, 2, 3 };
      |            var sum = nums.Sum();
      |        }
      |    }
      |}""".stripMargin

  val css: String =
    """/* Main theme */
      |:root {
      |  --primary: #3b82f6;
      |  --gap: 1.5rem;
      |}
      |body {
      |  font-family: "Inter", sans-serif;
      |  margin: 0;
      |  padding: var(--gap);
      |}
      |.container > .card:hover {
      |  transform: scale(1.05);
      |  opacity: 0.9;
      |}
      |@media (max-width: 768px) {
      |  .container { flex-direction: column; }
      |}""".stripMargin

  val go: String =
    """package main
      |
      |import "fmt"
      |
      |// Fibonacci returns the nth Fibonacci number.
      |func Fibonacci(n int) int {
      |	if n <= 1 {
      |		return n
      |	}
      |	a, b := 0, 1
      |	for i := 2; i <= n; i++ {
      |		a, b = b, a+b
      |	}
      |	return b
      |}
      |
      |func main() {
      |	result := Fibonacci(10)
      |	fmt.Printf("Fib(10) = %d\n", result)
      |}""".stripMargin

  val html: String =
    """<!DOCTYPE html>
      |<html lang="en">
      |<head>
      |  <meta charset="UTF-8">
      |  <title>Demo Page</title>
      |  <style>body { color: #333; }</style>
      |</head>
      |<body>
      |  <!-- Main content -->
      |  <h1 class="title">Hello</h1>
      |  <p id="intro">Count: <strong>42</strong></p>
      |  <script>console.log("loaded");</script>
      |</body>
      |</html>""".stripMargin

  val java: String =
    """import java.util.List;
      |import java.util.stream.Collectors;
      |/** A simple greeter. */
      |public class Greeter {
      |    private final String name;
      |    public Greeter(String name) { this.name = name; }
      |    public String greet() {
      |        return "Hello, " + name + "!";
      |    }
      |    public static void main(String[] args) {
      |        var items = List.of(1, 2, 3);
      |        int sum = items.stream().mapToInt(Integer::intValue).sum();
      |        System.out.println(new Greeter("World").greet());
      |    }
      |}""".stripMargin

  val javascript: String =
    """// Debounce utility
      |function debounce(fn, delay = 300) {
      |  let timer = null;
      |  return function (...args) {
      |    clearTimeout(timer);
      |    timer = setTimeout(() => fn.apply(this, args), delay);
      |  };
      |}
      |const greet = (name) => `Hello, ${name}!`;
      |const nums = [1, 2, 3];
      |const sum = nums.reduce((a, b) => a + b, 0);
      |console.log(greet("World"), sum);""".stripMargin

  val json: String =
    """{
      |  "name": "ssg",
      |  "version": "0.1.0",
      |  "keywords": ["scala", "highlight"],
      |  "count": 42,
      |  "enabled": true,
      |  "config": null,
      |  "nested": { "items": [1, 2.5, -3] }
      |}""".stripMargin

  val markdown: String =
    """# Heading
      |
      |A paragraph with **bold**, *italic*, and `code`.
      |
      |## List
      |
      |- Item one
      |- Item [two](https://example.com)
      |
      |```python
      |print("hello")
      |```""".stripMargin

  val python: String =
    """import math
      |from typing import List
      |
      |# Compute prime factors
      |def prime_factors(n: int) -> List[int]:
      |    factors: List[int] = []
      |    d = 2
      |    while d * d <= n:
      |        while n % d == 0:
      |            factors.append(d)
      |            n //= d
      |        d += 1
      |    if n > 1:
      |        factors.append(n)
      |    return factors
      |
      |result = prime_factors(360)
      |print(f"Factors: {result}")""".stripMargin

  val regex: String =
    """^(?:https?://)?(?:www\.)?([a-zA-Z0-9-]+)\.([a-z]{2,6})(?:/[\w.-]*)*(?:\?[^\s]*)?$""".stripMargin

  val ruby: String =
    """# Fibonacci with memoization
      |class Fibonacci
      |  def initialize
      |    @memo = {}
      |  end
      |
      |  def compute(n)
      |    return n if n <= 1
      |    @memo[n] ||= compute(n - 1) + compute(n - 2)
      |  end
      |end
      |
      |fib = Fibonacci.new
      |result = fib.compute(10)
      |puts "Fib(10) = #{result}" """.stripMargin

  val rust: String =
    """use std::collections::HashMap;
      |
      |/// Count word frequencies in text.
      |fn word_count(text: &str) -> HashMap<&str, usize> {
      |    let mut counts = HashMap::new();
      |    for word in text.split_whitespace() {
      |        *counts.entry(word).or_insert(0) += 1;
      |    }
      |    counts
      |}
      |
      |fn main() {
      |    let text = "hello world hello";
      |    let counts = word_count(text);
      |    println!("{:?}", counts);
      |}""".stripMargin

  val scala: String =
    """package example
      |
      |import scala.collection.mutable
      |
      |/** Word frequency counter. */
      |final case class Counter(text: String) {
      |  def counts: Map[String, Int] = {
      |    val m = mutable.Map.empty[String, Int]
      |    for (word <- text.split("\\s+")) {
      |      m(word) = m.getOrElse(word, 0) + 1
      |    }
      |    m.toMap
      |  }
      |}
      |
      |object Main extends App {
      |  val c = Counter("hello world hello")
      |  println(s"Counts: ${c.counts}")
      |}""".stripMargin

  val sql: String =
    """-- Top customers by total spend
      |SELECT
      |    c.name,
      |    COUNT(o.id) AS order_count,
      |    SUM(o.amount) AS total_spend
      |FROM customers c
      |JOIN orders o ON o.customer_id = c.id
      |WHERE o.created_at >= '2025-01-01'
      |  AND o.status IN ('completed', 'shipped')
      |GROUP BY c.name
      |HAVING SUM(o.amount) > 1000.00
      |ORDER BY total_spend DESC
      |LIMIT 10;""".stripMargin

  val toml: String =
    """# Project configuration
      |[package]
      |name = "ssg"
      |version = "0.1.0"
      |edition = "2024"
      |
      |[dependencies]
      |serde = { version = "1.0", features = ["derive"] }
      |tokio = { version = "1", features = ["full"] }
      |
      |[[bin]]
      |name = "main"
      |path = "src/main.rs"
      |
      |[profile.release]
      |opt-level = 3
      |lto = true""".stripMargin

  val typescript: String =
    """interface User {
      |  id: number;
      |  name: string;
      |  email?: string;
      |}
      |
      |// Fetch and filter active users
      |async function getActiveUsers(url: string): Promise<User[]> {
      |  const response = await fetch(url);
      |  const users: User[] = await response.json();
      |  return users.filter((u) => u.email !== undefined);
      |}
      |
      |const BASE_URL = "https://api.example.com";
      |getActiveUsers(`${BASE_URL}/users`).then(console.log);""".stripMargin

  val tsx: String =
    """import React, { useState } from "react";
      |
      |interface Props {
      |  initialCount: number;
      |  label?: string;
      |}
      |
      |const Counter: React.FC<Props> = ({ initialCount, label = "Count" }) => {
      |  const [count, setCount] = useState<number>(initialCount);
      |  return (
      |    <div className="counter">
      |      <span>{label}: {count}</span>
      |      <button onClick={() => setCount(count + 1)}>+</button>
      |    </div>
      |  );
      |};
      |
      |export default Counter;""".stripMargin

  val yaml: String =
    """# Application config
      |server:
      |  host: "0.0.0.0"
      |  port: 8080
      |  tls: true
      |
      |database:
      |  url: "postgres://localhost:5432/app"
      |  pool_size: 10
      |  timeout: 30.5
      |
      |features:
      |  - name: "auth"
      |    enabled: true
      |  - name: "cache"
      |    enabled: false
      |
      |logging:
      |  level: "info"
      |  format: "json" """.stripMargin

  val cmake: String =
    """cmake_minimum_required(VERSION 3.20)
      |project(MyApp VERSION 1.0 LANGUAGES CXX)
      |
      |# Set C++ standard
      |set(CMAKE_CXX_STANDARD 20)
      |set(CMAKE_CXX_STANDARD_REQUIRED ON)
      |
      |find_package(Threads REQUIRED)
      |
      |add_executable(myapp src/main.cpp)
      |target_link_libraries(myapp PRIVATE Threads::Threads)
      |
      |if(BUILD_TESTING)
      |  enable_testing()
      |  add_subdirectory(tests)
      |endif()""".stripMargin

  val dockerfile: String =
    """FROM rust:1.78-slim AS builder
      |WORKDIR /app
      |# Install dependencies
      |COPY Cargo.toml Cargo.lock ./
      |RUN mkdir src && echo "fn main(){}" > src/main.rs
      |RUN cargo build --release
      |COPY src/ src/
      |RUN cargo build --release
      |
      |FROM debian:bookworm-slim
      |COPY --from=builder /app/target/release/myapp /usr/local/bin/
      |EXPOSE 8080
      |ENV PORT=8080
      |CMD ["myapp", "--port", "8080"]""".stripMargin

  val dtd: String =
    """<!ELEMENT document (header, body, footer?)>
      |<!ELEMENT header (title, author+)>
      |<!ELEMENT title (#PCDATA)>
      |<!ELEMENT author (#PCDATA)>
      |<!ATTLIST author
      |  id    ID     #REQUIRED
      |  role  CDATA  #IMPLIED
      |>
      |<!ELEMENT body (section+)>
      |<!ELEMENT section (para | list)*>
      |<!ELEMENT para (#PCDATA | emphasis)*>
      |<!ELEMENT emphasis (#PCDATA)>
      |<!ELEMENT list (item+)>
      |<!ELEMENT item (#PCDATA)>
      |<!ELEMENT footer (#PCDATA)>""".stripMargin

  val elixir: String =
    """defmodule Counter do
      |  @moduledoc "A simple counter using Agent."
      |
      |  def start_link(initial \\ 0) do
      |    Agent.start_link(fn -> initial end, name: __MODULE__)
      |  end
      |
      |  def increment(amount \\ 1) do
      |    Agent.update(__MODULE__, &(&1 + amount))
      |  end
      |
      |  def value do
      |    Agent.get(__MODULE__, & &1)
      |  end
      |end
      |
      |{:ok, _} = Counter.start_link(42)
      |Counter.increment(8)
      |IO.puts("Value: #{Counter.value()}")""".stripMargin

  val erlang: String =
    """-module(counter).
      |-export([factorial/1, start/0]).
      |
      |%% Compute factorial recursively
      |factorial(0) -> 1;
      |factorial(N) when N > 0 ->
      |    N * factorial(N - 1).
      |
      |start() ->
      |    Result = factorial(10),
      |    io:format("Factorial(10) = ~p~n", [Result]),
      |    ok.""".stripMargin

  val haskell: String =
    """module Main where
      |
      |import Data.List (sort)
      |
      |-- | Compute the nth Fibonacci number.
      |fib :: Int -> Int
      |fib 0 = 0
      |fib 1 = 1
      |fib n = fib (n - 1) + fib (n - 2)
      |
      |main :: IO ()
      |main = do
      |  let result = fib 10
      |  putStrLn $ "Fib(10) = " ++ show result
      |  print (sort [3, 1, 4, 1, 5])""".stripMargin

  val julia: String =
    """module Stats
      |
      |# Compute mean and standard deviation
      |function mean_std(values::Vector{Float64})::Tuple{Float64, Float64}
      |    n = length(values)
      |    μ = sum(values) / n
      |    σ = sqrt(sum((x - μ)^2 for x in values) / n)
      |    return (μ, σ)
      |end
      |
      |end # module
      |
      |using .Stats
      |data = [1.0, 2.5, 3.7, 4.2]
      |m, s = Stats.mean_std(data)
      |println("Mean: $m, Std: $s")""".stripMargin

  val kotlin: String =
    """package demo
      |
      |/** A simple data class. */
      |data class User(val name: String, val age: Int)
      |
      |fun greet(user: User): String =
      |    "Hello, ${user.name}! You are ${user.age} years old."
      |
      |fun main() {
      |    val users = listOf(
      |        User("Alice", 30),
      |        User("Bob", 25)
      |    )
      |    val oldest = users.maxByOrNull { it.age }
      |    println(greet(oldest ?: User("Unknown", 0)))
      |}""".stripMargin

  val lua: String =
    """-- Fibonacci with memoization
      |local memo = {}
      |
      |function fibonacci(n)
      |  if n <= 1 then return n end
      |  if memo[n] then return memo[n] end
      |  memo[n] = fibonacci(n - 1) + fibonacci(n - 2)
      |  return memo[n]
      |end
      |
      |local result = fibonacci(10)
      |print(string.format("Fib(10) = %d", result))
      |
      |for i = 1, 5 do
      |  io.write(tostring(fibonacci(i)) .. " ")
      |end""".stripMargin

  val make: String =
    """# Build configuration
      |CC      := gcc
      |CFLAGS  := -Wall -O2 -std=c17
      |LDFLAGS := -lm
      |SRCDIR  := src
      |OBJDIR  := build
      |TARGET  := myapp
      |
      |SOURCES := $(wildcard $(SRCDIR)/*.c)
      |OBJECTS := $(SOURCES:$(SRCDIR)/%.c=$(OBJDIR)/%.o)
      |
      |.PHONY: all clean
      |
      |all: $(TARGET)
      |
      |$(TARGET): $(OBJECTS)
      |	$(CC) $(LDFLAGS) -o $@ $^
      |
      |$(OBJDIR)/%.o: $(SRCDIR)/%.c | $(OBJDIR)
      |	$(CC) $(CFLAGS) -c -o $@ $<
      |
      |$(OBJDIR):
      |	mkdir -p $@
      |
      |clean:
      |	rm -rf $(OBJDIR) $(TARGET)""".stripMargin

  val ocaml: String =
    """(* Binary search tree *)
      |type 'a tree =
      |  | Leaf
      |  | Node of 'a tree * 'a * 'a tree
      |
      |let rec insert x = function
      |  | Leaf -> Node (Leaf, x, Leaf)
      |  | Node (l, v, r) ->
      |    if x < v then Node (insert x l, v, r)
      |    else if x > v then Node (l, v, insert x r)
      |    else Node (l, v, r)
      |
      |let rec to_list = function
      |  | Leaf -> []
      |  | Node (l, v, r) -> to_list l @ [v] @ to_list r
      |
      |let () =
      |  let t = List.fold_left (fun t x -> insert x t) Leaf [3; 1; 4; 1; 5] in
      |  Printf.printf "Sorted: %s\n"
      |    (String.concat ", " (List.map string_of_int (to_list t)))""".stripMargin

  val ocamlInterface: String =
    """(** Binary search tree interface. *)
      |type 'a tree
      |
      |val empty : 'a tree
      |val insert : 'a -> 'a tree -> 'a tree
      |val member : 'a -> 'a tree -> bool
      |val to_list : 'a tree -> 'a list
      |val size : 'a tree -> int""".stripMargin

  val php: String =
    """<?php
      |namespace App\Service;
      |
      |/** Simple greeting service. */
      |class Greeter {
      |    private string $name;
      |
      |    public function __construct(string $name) {
      |        $this->name = $name;
      |    }
      |
      |    public function greet(): string {
      |        return "Hello, {$this->name}!";
      |    }
      |}
      |
      |$g = new Greeter("World");
      |echo $g->greet() . "\n";
      |$nums = [1, 2, 3];
      |$sum = array_sum($nums);
      |echo "Sum: $sum\n";""".stripMargin

  val phpOnly: String =
    """namespace App;
      |
      |function fibonacci(int $n): int {
      |    if ($n <= 1) return $n;
      |    $a = 0;
      |    $b = 1;
      |    for ($i = 2; $i <= $n; $i++) {
      |        [$a, $b] = [$b, $a + $b];
      |    }
      |    return $b;
      |}
      |
      |$result = fibonacci(10);
      |echo "Fib(10) = $result\n";""".stripMargin

  val r: String =
    """# Linear regression analysis
      |library(stats)
      |
      |compute_regression <- function(x, y) {
      |  model <- lm(y ~ x)
      |  coefs <- coef(model)
      |  r_squared <- summary(model)$r.squared
      |  list(slope = coefs[2], intercept = coefs[1], r2 = r_squared)
      |}
      |
      |x <- c(1, 2, 3, 4, 5)
      |y <- c(2.1, 4.0, 5.9, 8.1, 10.0)
      |result <- compute_regression(x, y)
      |cat(sprintf("Slope: %.2f, R²: %.4f\n", result$slope, result$r2))""".stripMargin

  val swift: String =
    """import Foundation
      |
      |/// A generic stack data structure.
      |struct Stack<Element> {
      |    private var items: [Element] = []
      |
      |    mutating func push(_ item: Element) {
      |        items.append(item)
      |    }
      |
      |    mutating func pop() -> Element? {
      |        return items.isEmpty ? nil : items.removeLast()
      |    }
      |
      |    var count: Int { items.count }
      |}
      |
      |var stack = Stack<Int>()
      |stack.push(42)
      |stack.push(17)
      |if let top = stack.pop() {
      |    print("Popped: \(top)")
      |}""".stripMargin

  val vim: String =
    """" Toggle line numbers
      |function! ToggleNumbers()
      |  if &number
      |    set nonumber
      |  else
      |    set number
      |  endif
      |endfunction
      |
      |" Basic settings
      |set tabstop=4
      |set shiftwidth=4
      |set expandtab
      |let g:mapleader = ","
      |let s:count = 42""".stripMargin

  val xml: String =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>demo</artifactId>
      |  <version>1.0.0</version>
      |  <!-- Dependencies -->
      |  <dependencies>
      |    <dependency>
      |      <groupId>junit</groupId>
      |      <artifactId>junit</artifactId>
      |      <version>4.13</version>
      |      <scope>test</scope>
      |    </dependency>
      |  </dependencies>
      |</project>""".stripMargin

  val zig: String =
    """const std = @import("std");
      |
      |/// Compute the greatest common divisor.
      |fn gcd(a: u64, b: u64) u64 {
      |    var x = a;
      |    var y = b;
      |    while (y != 0) {
      |        const temp = y;
      |        y = x % y;
      |        x = temp;
      |    }
      |    return x;
      |}
      |
      |pub fn main() !void {
      |    const result = gcd(48, 18);
      |    const stdout = std.io.getStdOut().writer();
      |    try stdout.print("GCD(48, 18) = {d}\n", .{result});
      |}""".stripMargin

  // ── Tier 3: Specialized languages ──────────────────────────────────────

  val arduino: String =
    """// LED blink with button
      |const int LED_PIN = 13;
      |const int BTN_PIN = 2;
      |
      |void setup() {
      |  pinMode(LED_PIN, OUTPUT);
      |  pinMode(BTN_PIN, INPUT_PULLUP);
      |  Serial.begin(9600);
      |}
      |
      |void loop() {
      |  if (digitalRead(BTN_PIN) == LOW) {
      |    digitalWrite(LED_PIN, HIGH);
      |    delay(500);
      |    digitalWrite(LED_PIN, LOW);
      |    Serial.println("Blink!");
      |  }
      |}""".stripMargin

  val bicep: String =
    """// Azure Storage Account
      |param location string = resourceGroup().location
      |param storageName string
      |
      |@allowed(['Standard_LRS', 'Standard_GRS'])
      |param skuName string = 'Standard_LRS'
      |
      |resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
      |  name: storageName
      |  location: location
      |  kind: 'StorageV2'
      |  sku: { name: skuName }
      |}
      |
      |var endpoint = storageAccount.properties.primaryEndpoints.blob
      |output blobEndpoint string = endpoint""".stripMargin

  val cairo: String =
    """// StarkNet ERC20 token
      |#[starknet::contract]
      |mod ERC20 {
      |    use starknet::ContractAddress;
      |
      |    #[storage]
      |    struct Storage {
      |        balances: LegacyMap::<ContractAddress, felt252>,
      |        total_supply: felt252,
      |    }
      |
      |    #[external(v0)]
      |    fn transfer(ref self: ContractState, to: ContractAddress, amount: felt252) {
      |        let sender = starknet::get_caller_address();
      |        self.balances.write(sender, self.balances.read(sender) - amount);
      |        self.balances.write(to, self.balances.read(to) + amount);
      |    }
      |}""".stripMargin

  val cpon: String =
    """<1:1>i{
      |  "id": 42,
      |  "name": "sensor-01",
      |  "active": true,
      |  "temperature": 23.5,
      |  "tags": ["indoor", "floor-2"],
      |  "config": {
      |    "interval": 1000,
      |    "threshold": null
      |  },
      |  "data": b"\x01\x02\x03"
      |}""".stripMargin

  val cuda: String =
    """#include <stdio.h>
      |// Vector addition kernel
      |__global__ void vecAdd(float *a, float *b, float *c, int n) {
      |    int idx = blockIdx.x * blockDim.x + threadIdx.x;
      |    if (idx < n) {
      |        c[idx] = a[idx] + b[idx];
      |    }
      |}
      |
      |__device__ float square(float x) { return x * x; }
      |
      |int main() {
      |    int N = 1024;
      |    float *d_a, *d_b, *d_c;
      |    cudaMalloc(&d_a, N * sizeof(float));
      |    vecAdd<<<(N+255)/256, 256>>>(d_a, d_b, d_c, N);
      |    cudaDeviceSynchronize();
      |}""".stripMargin

  val embeddedTemplate: String =
    """<html>
      |<body>
      |  <h1><%= @title %></h1>
      |  <% if @items.any? %>
      |    <ul>
      |      <% @items.each do |item| %>
      |        <li class="item"><%= item.name %></li>
      |      <% end %>
      |    </ul>
      |  <% else %>
      |    <p>No items found.</p>
      |  <% end %>
      |  <footer><%# This is a comment %></footer>
      |</body>
      |</html>""".stripMargin

  val func: String =
    """;; Simple wallet contract
      |#include "stdlib.fc"
      |
      |() recv_internal(int msg_value, cell in_msg, slice in_msg_body) impure {
      |    slice sender = in_msg.begin_parse();
      |    int op = in_msg_body~load_uint(32);
      |    if (op == 0) { ;; transfer
      |        int amount = in_msg_body~load_coins();
      |        slice dest = in_msg_body~load_msg_addr();
      |        send_raw_message(dest, amount);
      |    }
      |}
      |
      |int get_balance() method_id {
      |    return get_balance().pair_first();
      |}""".stripMargin

  val gitattributes: String =
    """# Auto-detect text files
      |* text=auto
      |
      |# Source files
      |*.scala text diff=scala
      |*.java text diff=java
      |*.py text diff=python
      |
      |# Binary files
      |*.png binary
      |*.jpg binary
      |*.jar binary
      |
      |# Line endings
      |*.sh text eol=lf
      |*.bat text eol=crlf""".stripMargin

  val glsl: String =
    """#version 330 core
      |// Phong lighting fragment shader
      |uniform vec3 lightPos;
      |uniform vec3 viewPos;
      |uniform sampler2D diffuseMap;
      |
      |in vec3 FragPos;
      |in vec3 Normal;
      |in vec2 TexCoords;
      |out vec4 FragColor;
      |
      |void main() {
      |    vec3 norm = normalize(Normal);
      |    vec3 lightDir = normalize(lightPos - FragPos);
      |    float diff = max(dot(norm, lightDir), 0.0);
      |    vec3 color = diff * texture(diffuseMap, TexCoords).rgb;
      |    FragColor = vec4(color, 1.0);
      |}""".stripMargin

  val gosum: String =
    """github.com/google/go-cmp v0.5.9 h1:O2Tfq5qg4qc4AmwVlvv0oLiVAGB7enBSJ2x2DqQFi38=
      |github.com/google/go-cmp v0.5.9/go.mod h1:17dUlkBOakJ0+DkrSSNjCkIjxS6bF9aB9XM8orkber4=
      |golang.org/x/exp v0.0.0-20230713183714-613f0c0eb8a1 h1:abc123=
      |golang.org/x/exp v0.0.0-20230713183714-613f0c0eb8a1/go.mod h1:def456=
      |golang.org/x/tools v0.12.0 h1:ghijkl=
      |golang.org/x/tools v0.12.0/go.mod h1:mnopqr=""".stripMargin

  val hare: String =
    """use fmt;
      |use os;
      |
      |// Compute factorial iteratively
      |export fn factorial(n: u64) u64 = {
      |    let result: u64 = 1;
      |    for (let i: u64 = 2; i <= n; i += 1) {
      |        result *= i;
      |    };
      |    return result;
      |};
      |
      |export fn main() void = {
      |    const n: u64 = 10;
      |    const result = factorial(n);
      |    fmt::printfln("{}! = {}", n, result)!;
      |};""".stripMargin

  val jsdoc: String =
    """/**
      | * Calculate the area of a rectangle.
      | * @param {number} width - The width of the rectangle.
      | * @param {number} height - The height of the rectangle.
      | * @returns {number} The area of the rectangle.
      | * @throws {RangeError} If width or height is negative.
      | * @example
      | * const area = calculateArea(5, 3);
      | * // => 15
      | * @since 1.0.0
      | * @type {Function}
      | */""".stripMargin

  val kconfig: String =
    """# Network configuration
      |menuconfig NET
      |    bool "Networking support"
      |    default y
      |
      |if NET
      |
      |config TCP
      |    bool "TCP/IP networking"
      |    depends on NET
      |    select INET
      |    help
      |      Enable TCP/IP protocol support.
      |
      |config UDP
      |    bool "UDP support"
      |    depends on NET
      |    default y
      |
      |endif # NET""".stripMargin

  val kdl: String =
    """// Application config in KDL
      |package {
      |    name "my-app"
      |    version "1.2.3"
      |    authors "Alice" "Bob"
      |}
      |
      |dependencies {
      |    serde version="1.0" features=("derive" "json")
      |    tokio version="1.0" optional=true
      |}
      |
      |server host="0.0.0.0" port=8080 {
      |    tls enabled=true cert="/path/to/cert.pem"
      |}""".stripMargin

  val luadoc: String =
    """---@class Animal
      |---@field name string The animal's name
      |---@field age integer The age in years
      |local Animal = {}
      |
      |---Create a new Animal instance.
      |---@param name string The name
      |---@param age integer The age
      |---@return Animal
      |function Animal.new(name, age)
      |    local self = setmetatable({}, { __index = Animal })
      |    self.name = name
      |    self.age = age
      |    return self
      |end""".stripMargin

  val luap: String =
    """%d+%.%d+
      |^%a[%w_]*$
      |%b()
      |[%l%u]+
      |%%escaped
      |%d%d/%d%d/%d%d%d%d
      |(%w+)%s*=%s*(%w+)
      |[^%s]+""".stripMargin

  val luau: String =
    """--!strict
      |-- Typed Roblox Lua
      |export type Vector3 = {
      |    x: number,
      |    y: number,
      |    z: number,
      |}
      |
      |local function magnitude(v: Vector3): number
      |    return math.sqrt(v.x^2 + v.y^2 + v.z^2)
      |end
      |
      |type Callback = (result: number) -> ()
      |
      |local pos: Vector3 = { x = 1, y = 2, z = 3 }
      |local len = magnitude(pos)
      |print(`Length: {len}`)""".stripMargin

  val objc: String =
    """#import <Foundation/Foundation.h>
      |// Simple greeter class
      |@interface Greeter : NSObject
      |@property (nonatomic, strong) NSString *name;
      |- (instancetype)initWithName:(NSString *)name;
      |- (NSString *)greet;
      |@end
      |
      |@implementation Greeter
      |- (instancetype)initWithName:(NSString *)name {
      |    self = [super init];
      |    if (self) { _name = name; }
      |    return self;
      |}
      |- (NSString *)greet {
      |    return [NSString stringWithFormat:@"Hello, %@!", self.name];
      |}
      |@end""".stripMargin

  val odin: String =
    """package main
      |
      |import "core:fmt"
      |import "core:math"
      |
      |// Compute distance between two points
      |Point :: struct {
      |    x, y: f64,
      |}
      |
      |distance :: proc(a, b: Point) -> f64 {
      |    dx := b.x - a.x
      |    dy := b.y - a.y
      |    return math.sqrt(dx*dx + dy*dy)
      |}
      |
      |main :: proc() {
      |    p1 := Point{0, 0}
      |    p2 := Point{3, 4}
      |    fmt.printf("Distance: %f\n", distance(p1, p2))
      |}""".stripMargin

  val po: String =
    """# Translation file for MyApp
      |msgid ""
      |msgstr ""
      |"Content-Type: text/plain; charset=UTF-8\n"
      |"Language: fr\n"
      |
      |#: src/main.c:42
      |msgid "Hello, World!"
      |msgstr "Bonjour le monde !"
      |
      |#: src/main.c:55
      |#, c-format
      |msgid "Found %d item"
      |msgid_plural "Found %d items"
      |msgstr[0] "Trouvé %d élément"
      |msgstr[1] "Trouvé %d éléments" """.stripMargin

  val pony: String =
    """actor Counter
      |  var _count: U64 = 0
      |
      |  be increment(amount: U64 = 1) =>
      |    _count = _count + amount
      |
      |  be get_count(callback: {(U64)} val) =>
      |    callback(_count)
      |
      |  fun ref reset(): U64 =>
      |    let old = _count
      |    _count = 0
      |    old
      |
      |actor Main
      |  new create(env: Env) =>
      |    let counter = Counter
      |    counter.increment(5)
      |    counter.get_count({(n: U64) => env.out.print(n.string())})""".stripMargin

  val printf: String =
    """%d %i %u %f %e %g
      |%s %c %p %n
      |%10d %-10s %+.2f
      |%05d %#x %08.3f
      |%ld %lld %zu %hd
      |%*d %.*f
      |%%literal percent
      |%[^\n]""".stripMargin

  val properties: String =
    """# Application configuration
      |app.name=MyApplication
      |app.version=2.1.0
      |
      |# Database settings
      |db.host=localhost
      |db.port=5432
      |db.name=mydb
      |db.user=admin
      |db.password=secret123
      |
      |# Feature flags
      |feature.cache.enabled=true
      |feature.cache.ttl=3600
      |feature.debug=false""".stripMargin

  val puppet: String =
    """# Web server configuration
      |class webserver (
      |  String $docroot = '/var/www/html',
      |  Integer $port = 80,
      |) {
      |  package { 'nginx':
      |    ensure => installed,
      |  }
      |
      |  file { $docroot:
      |    ensure => directory,
      |    owner  => 'www-data',
      |    mode   => '0755',
      |  }
      |
      |  service { 'nginx':
      |    ensure    => running,
      |    enable    => true,
      |    subscribe => File[$docroot],
      |  }
      |}
      |
      |node 'web01.example.com' {
      |  include webserver
      |}""".stripMargin

  val qmldir: String =
    """module MyComponents
      |
      |Button 1.0 Button.qml
      |Switch 1.0 Switch.qml
      |Slider 1.2 Slider.qml
      |
      |singleton Theme 1.0 Theme.qml
      |internal PrivateHelper PrivateHelper.qml
      |
      |plugin mycomponentsplugin
      |classname MyComponentsPlugin
      |typeinfo plugins.qmltypes
      |depends QtQuick 2.15
      |designersupported""".stripMargin

  val requirements: String =
    """# Core dependencies
      |flask==2.3.2
      |sqlalchemy>=2.0,<3.0
      |pydantic~=2.1
      |
      |# Async support
      |uvicorn[standard]>=0.22.0
      |httpx!=0.24.0
      |
      |# Development
      |pytest>=7.0
      |black==23.7.0
      |mypy
      |
      |# From git
      |-e git+https://github.com/user/repo.git@main#egg=mylib""".stripMargin

  val ron: String =
    """// Game configuration
      |GameConfig(
      |    title: "My Game",
      |    window: WindowConfig(
      |        width: 1920,
      |        height: 1080,
      |        fullscreen: false,
      |    ),
      |    players: [
      |        Player(name: "Alice", score: 42, active: true),
      |        Player(name: "Bob", score: 17, active: false),
      |    ],
      |    difficulty: Hard,
      |    version: (1, 2, 3),
      |)""".stripMargin

  val scss: String =
    """// Theme variables and mixins
      |$primary: #3b82f6;
      |$border-radius: 4px;
      |
      |@mixin respond-to($breakpoint) {
      |  @media (max-width: $breakpoint) { @content; }
      |}
      |
      |.card {
      |  border: 1px solid darken($primary, 10%);
      |  border-radius: $border-radius;
      |
      |  &:hover {
      |    transform: scale(1.02);
      |  }
      |
      |  &__title {
      |    font-size: 1.5rem;
      |    color: $primary;
      |  }
      |
      |  @include respond-to(768px) {
      |    padding: 1rem;
      |  }
      |}""".stripMargin

  val squirrel: String =
    """// Squirrel game script
      |class Entity {
      |    name = null
      |    health = 100
      |
      |    constructor(name) {
      |        this.name = name
      |    }
      |
      |    function takeDamage(amount) {
      |        health -= amount
      |        if (health <= 0) {
      |            print(name + " is defeated!")
      |        }
      |    }
      |}
      |
      |local hero = Entity("Player")
      |hero.takeDamage(30)
      |print(format("HP: %d", hero.health))""".stripMargin

  val starlark: String =
    """# BUILD file for Bazel
      |load("@rules_java//java:defs.bzl", "java_binary", "java_library")
      |
      |java_library(
      |    name = "lib",
      |    srcs = glob(["src/**/*.java"]),
      |    deps = [
      |        "//third_party:guava",
      |        "@maven//:com_google_protobuf_protobuf_java",
      |    ],
      |    visibility = ["//visibility:public"],
      |)
      |
      |java_binary(
      |    name = "app",
      |    main_class = "com.example.Main",
      |    deps = [":lib"],
      |)""".stripMargin

  val svelte: String =
    """<script>
      |  export let name = "World";
      |  let count = 0;
      |
      |  function increment() {
      |    count += 1;
      |  }
      |</script>
      |
      |<main>
      |  <h1>Hello {name}!</h1>
      |  {#if count > 0}
      |    <p>Count: {count}</p>
      |  {:else}
      |    <p>Click to start counting.</p>
      |  {/if}
      |  <button on:click={increment}>+1</button>
      |</main>
      |
      |<style>
      |  h1 { color: #ff3e00; }
      |</style>""".stripMargin

  val ungrammar: String =
    """// Simplified expression grammar
      |SourceFile = Item*
      |
      |Item =
      |  Function
      || StructDef
      |
      |Function = 'fn' Name ParamList '->' Type Block
      |
      |ParamList = '(' Param* ')'
      |Param = Name ':' Type
      |
      |StructDef = 'struct' Name '{' Field* '}'
      |Field = Name ':' Type
      |
      |Type = Name | 'int' | 'bool'
      |Name = 'ident'
      |Block = '{' Expr* '}'
      |Expr = Name | 'number' | BinExpr
      |BinExpr = Expr '+' Expr""".stripMargin

  val yuck: String =
    """; EWW status bar widget
      |(defvar time_poll "date '+%H:%M'")
      |
      |(defwidget clock []
      |  (box :class "clock"
      |    :orientation "h"
      |    :space-evenly false
      |    (label :text {time_poll})))
      |
      |(defwidget bar []
      |  (centerbox :orientation "h"
      |    (workspaces)
      |    (clock)
      |    (box :halign "end"
      |      (label :text "vol: ${volume}%"))))
      |
      |(defwindow main
      |  :monitor 0
      |  :geometry (geometry :x "0%" :y "0%" :width "100%" :height "32px")
      |  (bar))""".stripMargin
}
