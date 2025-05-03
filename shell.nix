{ pkgs ? import <nixpkgs> { } }:

let
  clojure = pkgs.clojure;
  inherit (clojure) jdk;
in
pkgs.mkShell {
  buildInputs = [ clojure jdk pkgs.clojure-lsp pkgs.nodejs pkgs.yarn ];
  JAVA_HOME = jdk.home;
}
