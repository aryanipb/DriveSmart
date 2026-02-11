#!/bin/bash
# CONFIGURATION - Adjust these to match your system
NDK=/home/aryan/Android/ndk/26.1.10909125
PREFIX=/home/aryan/Vaults/smartphone_prototype/vanetza-deps/install-android
BOOST_SRC=/home/aryan/Vaults/smartphone_prototype/vanetza/boost # or wherever the source is

cd $BOOST_SRC
./bootstrap.sh

# Create the user-config for Android
echo "using gcc : android : $NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android24-clang++ ;" > user-config.jam

./b2 install \
    --prefix=$PREFIX \
    --user-config=user-config.jam \
    toolset=gcc-android \
    target-os=android \
    architecture=arm \
    abi=aapcs \
    address-model=64 \
    link=static \
    threading=multi \
    --with-date_time --with-system --with-program_options
