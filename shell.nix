{ pkgs ? import <nixpkgs> { } }:

let
  clojure = pkgs.clojure;
  inherit (clojure) jdk;
in
pkgs.mkShell {
  buildInputs = [ clojure jdk pkgs.clojure-lsp ];
  JAVA_HOME = jdk.home;
}
