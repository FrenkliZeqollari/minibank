FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# The base image ships an apt source for Yarn whose signing key is out of date. With it, every
# `apt-get update` fails ("NO_PUBKEY 62D54FD4003F6525 ... is not signed"), and so does every dev
# container feature that installs packages, e.g. docker-in-docker. This project does not use Yarn,
# so the source is removed before the features run.
RUN rm -f /etc/apt/sources.list.d/yarn.list
