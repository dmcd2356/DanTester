#!/bin/bash

# assumes you are running this script in a directory that contains subdirectories for each of
# the test programs you wish to run.
# - PROJECT is the project name you specify as an argument and should be one of the subdirectory names.
# - The subdirectory should contain a lib directory containing all of the jar files needed
#  (the application as well as the libraries).
# - The danalyzer.jar file is assumed to be located at ${DANPATH}/dist
# - The file to run will be in the PROJECT subdirectory having the name ${PROJECT}-dan-ed.jar
#
# these options help catch errors.
# 'nounset' throws an error if a parameter being used is undefined.
# 'errexit' causes any error condition to terminate the script, so it doesn't continue running.
set -o nounset
#set -o errexit

DANPATH="/mnt/sdb1/Projects/ISSTAC/repo/danalyzer/"
#DANPATH="/home/dmcd2356/Projects/ISSTAC/repo/danalyzer/"

# initialize the classpath to run the danalyzer
#
function init_classpath
{
    # create classpath
    CLASSPATH=""
    add_to_classpath "${PROJECT}-dan-ed.jar"

    # add the libraries required for danalyzed file
    add_to_classpath "${DANPATH}lib/commons-io-2.5.jar"
    add_to_classpath "${DANPATH}lib/asm-all-5.2.jar"
    add_to_classpath "${DANPATH}lib/com.microsoft.z3.jar"
    add_to_classpath "${DANPATH}lib/jgraphx.jar"
}

# adds specified jar file to $CLASSPATH
#
# inputs: $1 = jar file (plus full path) to add
#
function add_to_classpath
{
    if [[ -z $1 ]]; then
        return
    fi

    if [[ -z ${CLASSPATH} ]]; then
        CLASSPATH="$1"
    else
        CLASSPATH="${CLASSPATH}:$1"
    fi
}

# adds all of the jar files in the specified path to $CLASSPATH
#
# inputs: $1 = path of lib files to add
#
function read_libdir
{
    if [[ "$1" == "" ]]; then
        # if no path given, use current path
        jarpath="*.jar"
    elif [[ ! -d "$1" ]]; then
        # exit if path not found
        echo "invalid path: $1"
        return
    else
        jarpath=$1/*.jar
    fi

    # exit if no jars in current path
    count=$( ls ${jarpath} 2> /dev/null | wc -l )
    echo "${count} jars found in path: $1"
    if [[ ${count} -eq 0 ]]; then
        return
    fi

    while read -r jarfile; do
        select=(${jarfile})
        select="${select##*/}"      # remove leading '/' from filename
        add_to_classpath ${jarfile}
    done < <(ls ${jarpath})
}

#=======================================================================
# start here:
# run the instrumented test
if [[ $# -eq 0 ]]; then
    echo "runtest.sh [-t] <test_type> <test_number>"
    echo ""
    echo "where: <test_type>   = 1 for Uninstrumented tests, 2 for NewObject tests"
    echo "       <test_number> = the test number for the specified test"
    echo "       -t (optional) = display helpful info for debugging"
    echo "       -T (optional) = display extensive helpful info for debugging"
    echo "       -i (optional) = run danalyzer on DanTest before running test"
    echo ""
    exit 0
fi

# read options
COMMAND=()
TESTMODE=""
INSTRUMENT=0
while [[ $# -gt 0 ]]; do
    key="$1"
    case ${key} in
        -i)
            INSTRUMENT=1
            shift
            ;;
        -t)
            TESTMODE="-t"
            shift
            ;;
        -T)
            TESTMODE="-T"
            shift
            ;;
        *)
            COMMAND+=("$1")
            shift
            ;;
    esac
done
ARGLIST="${COMMAND[@]:0}"

# save current path
CURDIR=$(pwd 2>&1)

# select the DanTest project & descend into its main dir
PROJECT="DanTest"
MAINCLASS="dantest.DanTest"
RAWFILE="testraw.txt"
OUTFILE="testoutput.txt"

# perform the following in the DanTest folder
cd "DanTest"

# set danalyzer properties for this project to enable "TEST" and "UNINSTR" messages
CFGFILE="danfig"
if [ -f ${CFGFILE} ]; then
    rm -f ${CFGFILE}
fi
echo "#! DANALYZER SYMBOLIC EXPRESSION LIST" > ${CFGFILE}
echo "Thread:" >> ${CFGFILE}
echo "DebugMode:      STDOUT" >> ${CFGFILE}
echo "DebugFlags:     TEST, UNINST" >> ${CFGFILE}
echo "TriggerAuto:    0" >> ${CFGFILE}
echo "TriggerAnyMeth: 0" >> ${CFGFILE}
echo "TriggerOnCall:  0" >> ${CFGFILE}

# setup the classpath
init_classpath
add_to_classpath "../DanTestLib/dist/DanTestLib.jar"

# if not found or requested, danalyze the DanTest program
if [[ ${INSTRUMENT} -eq 1 || ! -f ${PROJECT}-dan-ed.jar ]]; then
    if [ ! -f "dist/${PROJECT}.jar" ]; then
        echo "${PROJECT}.jar not found in ${PROJECT}/dist folder"
        exit 1
    fi

    # run danalyzer on the application jar to instrument it
    echo "running danalyzer on DanTest"
    java -cp ${CLASSPATH} -jar ${DANPATH}dist/danalyzer.jar dist/${PROJECT}.jar 

    # move the instrumented file up to main dir of PROJECT
    mv dist/${PROJECT}-dan-ed.jar ${PROJECT}-dan-ed.jar
fi

# run the DanTest program and capture output to file
echo "running danalyzed DanTest for specified test"
java -Xverify:none -cp ${CLASSPATH} ${MAINCLASS} ${ARGLIST} > "../DanParse/${RAWFILE}"

# perform the following in the DanParse folder
cd "../DanParse"

# make sure the test was valid
valid=0
while IFS='' read -r line || [[ -n "$line" ]]; do
    if [[ "${line}" == "EXPECTED:"* && valid -eq 0 ]]; then
        valid=1
    fi
    if [[ "${line}" == "TESTEXIT"* && valid -eq 1 ]]; then
        valid=2
    fi
    if [[ "${line}" == "!!!INVALID" ]]; then
        echo "Invalid test selection"
        exit 1
    fi
done < "${RAWFILE}"
if [[ valid -ne 2 ]]; then
    echo "Error running DanTest: Expected messages not found"
    exit 1
fi

# now we read through the output file and extract the essentials
java -jar dist/DanParse.jar ${TESTMODE} ${RAWFILE} ${OUTFILE}
status=$(head -n 1 ${OUTFILE})
echo "Test result: ${status}"

# restore original dir
cd ${CURDIR}
exit 0

