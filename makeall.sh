#!/bin/bash

# assumes you are running this script from the DanTester main directory that contains folders
# for each of the sub-projects.
# - The danalyzer.jar file is assumed to be located at ${DANPATH}/dist
#
# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
#set -o errexit

#DANPATH="/mnt/sdb1/Projects/ISSTAC/repo/danalyzer/"
DANPATH="/home/dmcd2356/Projects/ISSTAC/repo/danalyzer/"

function build
{
    echo "- building ${1}"
    if [[ ${1} == "danalyzer" ]]; then
        cd "${DANPATH}"
    else
        cd "${1}"
    fi
    ant clean &> /dev/null
    ant jar &> /dev/null
    if [[ $? -ne 0 ]]; then
        echo "ERROR: command failure"
        cd ${CURDIR}
        exit 1
    fi
    cd "${CURDIR}"
}

# save current path
CURDIR=$(pwd 2>&1)

# verify danalyzer path
if [ ! -d "${DANPATH}" ]; then
    echo "danalyzer path invalid: ${DANPATH}"
    exit 1
fi

build "danalyzer"
build "DanParse"
build "DanTestLib"

# copy lib to DanTest lib path
if [ ! -d DanTest/lib ]; then
    mkdir DanTest/lib
fi
cp DanTestLib/dist/DanTestLib.jar DanTest/lib

build "DanTest"

# since we re-built DanTest, remove old danalyzed version so we will re-danalyze it
rm -f DanTest/DanTest-dan-ed.jar

# restore original dir and remove any temp files created
cd ${CURDIR}
exit 0

