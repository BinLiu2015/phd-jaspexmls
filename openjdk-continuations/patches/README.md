# Patches for first-class continuations in OpenJDK

These patches apply on top of the normal OpenJDK mlvm source tree.

See <https://wikis.oracle.com/display/mlvm/Building> for more info on where to get the sources.

The patches themselves apply on top of a very specific checkout of openjdk:

    find sources/ -type d -name .hg -exec bash -c "cd '{}' && hg update --date '<2010-08-01'" \;
    find sources/ -type d -name .hg -exec bash -c "cd '{}' && hg update 4afae810a801" \; # hotspot

The patches should be applied in order.

To compile for 64-bit linux I did:

    export ALT_BOOTDIR=/usr/lib64/jvm/java ALT_JDK_IMPORT_PATH=/usr/lib64/jvm/java ARCH_DATA_MODEL=64 BUILD_HOTSPOT=true BUILD_JAXP=false BUILD_JAXWS=false BUILD_JDK=true BUILD_LANGTOOLS=true HOTSPOT_BUILD_JOBS=4 LANG=C PARALLEL_COMPILE_JOBS=4
    export -n JAVA_HOME
    make

...and for 32-bit with fastdebug:

    export ALT_BOOTDIR=/usr/lib/jvm/java ALT_JDK_IMPORT_PATH=/usr/lib/jvm/java ARCH_DATA_MODEL=32 BUILD_HOTSPOT=true BUILD_JAXP=false BUILD_JAXWS=false BUILD_JDK=true BUILD_LANGTOOLS=true HOTSPOT_BUILD_JOBS=2 LANG=C PARALLEL_COMPILE_JOBS=2 SKIP_DEBUG_BUILD=true SKIP_FASTDEBUG_BUILD=false DEBUG_NAME=fastdebug
    export -n JAVA_HOME
    make

Feel free to ask me for help on getting the exact sources and building them! I may get around to pushing them to a git repo someday too.
