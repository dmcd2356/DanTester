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

function build_folder
{
    echo "- building $1"
    if [[ "$1" == "danalyzer" ]]; then
        cd "${DANPATH}"
    else
        cd "$1"
    fi
    ant jar &1> /dev/null
    if [[ $? -ne 0 ]]; then
        echo "ERROR: command failure"
        cd "${CURDIR}"
        exit_cleanup
        exit 1
    fi
    cd "${CURDIR}"
}

function exit_cleanup
{
    # restore the original directory
    cd ${CURDIR}

    # remove the temp files produced
    rm -f DanTest/${CFGFILE}
}

function helpmsg
{
    echo "runtest.sh [options] <test_type> <test_number>"
    echo ""
    echo "where: <test_type>   = 1 for Uninstrumented tests, 2 for NewObject tests"
    echo "       <test_number> = the test number for the specified test"
    echo "options:"
    echo "       -t  = display state change info for debugging test"
    echo "       -T  = display state and parsing info for debugging test"
    echo "       -i  = run danalyzer on DanTest before running test"
    echo "       -l  = force rebuild of DanTestLib and DanTest prior to running"
    echo ""
}

#=======================================================================
# start here:
# run the instrumented test
if [[ $# -eq 0 ]]; then
    helpmsg
    exit 0
fi

# read options
COMMAND=()
TESTMODE=""
INSTRUMENT=0
UPDATELIB=0
ARGCOUNT=0
while [[ $# -gt 0 ]]; do
    key="$1"
    case ${key} in
        -i)
            INSTRUMENT=1
            shift
            ;;
        -l)
            UPDATELIB=1
            shift
            ;;
        -t)
            if [[ "${TESTMODE}" == "" ]]; then
                TESTMODE="-t"
            fi
            shift
            ;;
        -T)
            TESTMODE="-T"
            shift
            ;;
        *)
            COMMAND+=("$1")
            shift
            ARGCOUNT=`expr ${ARGCOUNT} + 1`
            ;;
    esac
done
if [[ ${ARGCOUNT} -ne 2 ]]; then
    echo "ERROR: invalid number of arguments given"
    echo ""
    helpmsg
    exit 1
fi
ARGLIST="${COMMAND[@]:0}"

# save current path
CURDIR=$(pwd 2>&1)

# specify the names of the files for DanParse to produce
CFGFILE="danfig"
RAWFILE="testraw.txt"
OUTFILE="testoutput.txt"

# verify danalyzer path
if [ ! -d "${DANPATH}" ]; then
    echo "danalyzer path invalid: ${DANPATH}"
    exit 1
    if [ ! -f "${DANPATH}/dist/danalyzer.jar" ]; then
        echo "- building danalyzer"
        cd "${DANPATH}"
        ant jar 2>&1 /dev/null
        if [[ $? -ne 0 ]]; then
            echo "ERROR: command failure"
            exit_cleanup
            exit 1
        fi
        cd "${CURDIR}"
    fi
fi

# if any of the jar files are missing, build them now
FOLDER="DanParse"
if [ ! -f "${FOLDER}/dist/${FOLDER}.jar" ]; then
    echo "- building ${FOLDER}"
    cd "${FOLDER}"
    ant jar &> /dev/null
    if [[ $? -ne 0 ]]; then
        echo "ERROR: command failure"
        exit_cleanup
        exit 1
    fi
    cd "${CURDIR}"
fi

FOLDER="DanTestLib"
if [[ ! -f "${FOLDER}/dist/${FOLDER}.jar" || ${UPDATELIB} -eq 1 ]]; then
    # build DanTest library project
    echo "- building ${FOLDER}"
    cd "${FOLDER}"
    ant jar &> /dev/null
    if [[ $? -ne 0 ]]; then
        echo "ERROR: command failure"
        exit_cleanup
        exit 1
    fi
    cd "${CURDIR}"

    # copy lib to DanTest lib path
    if [ ! -d DanTest/lib ]; then
        mkdir DanTest/lib
    fi
    cp ${FOLDER}/dist/${FOLDER}.jar DanTest/lib

    # remove DanTest.jar file to force re-build of it
    rm -f DanTest/dist/DanTest.jar
fi

FOLDER="DanTest"
if [[ ! -f "${FOLDER}/dist/${FOLDER}.jar" || ${UPDATELIB} -eq 1 ]]; then
    # build DanTest project
    echo "- building ${FOLDER}"
    cd "${FOLDER}"
    ant jar &> /dev/null
    if [[ $? -ne 0 ]]; then
        echo "ERROR: command failure"
        exit_cleanup
        exit 1
    fi
    cd "${CURDIR}"

    # since we re-built DanTest, remove old danalyzed version so we will re-danalyze it
    rm -f ${FOLDER}/${FOLDER}-dan-ed.jar
fi

# perform the following in the DanTest folder
cd "DanTest"

    # set danalyzer properties for this project to enable "TEST" and "UNINSTR" messages
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
    CLASSPATH=""
    add_to_classpath "DanTest-dan-ed.jar"
    add_to_classpath "lib/DanTestLib.jar"

    # add the libraries required for danalyzed files
    add_to_classpath "${DANPATH}lib/commons-io-2.5.jar"
    add_to_classpath "${DANPATH}lib/asm-all-5.2.jar"
    add_to_classpath "${DANPATH}lib/com.microsoft.z3.jar"
    add_to_classpath "${DANPATH}lib/jgraphx.jar"

    # if not found or requested, danalyze the DanTest program
    FOLDER="DanTest"
    if [[ ${INSTRUMENT} -eq 1 || ! -f ${FOLDER}-dan-ed.jar ]]; then
        # run danalyzer on the application jar to instrument it
        echo "- instrumenting ${FOLDER} for danalyzer"
        java -cp ${CLASSPATH} -jar ${DANPATH}dist/danalyzer.jar dist/${FOLDER}.jar
        if [[ $? -ne 0 ]]; then
            echo "ERROR: command failure"
            exit_cleanup
            exit 1
        fi

        # move the instrumented file up to main dir
        mv dist/${FOLDER}-dan-ed.jar ${FOLDER}-dan-ed.jar
    fi

    # run the DanTest program and capture output to file
    echo "- running instrumented DanTest for test ${ARGLIST}"
    java -Xverify:none -cp ${CLASSPATH} dantest.DanTest ${ARGLIST} > ../DanParse/${RAWFILE}
    if [[ $? -ne 0 ]]; then
        echo "ERROR: command failure"
        exit_cleanup
        exit 1
    fi

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
            exit_cleanup
            exit 1
        fi
    done < "${RAWFILE}"
    if [[ valid -ne 2 ]]; then
        echo "ERROR: Expected messages not found"
        exit_cleanup
        exit 1
    fi

    # now we read through the output file and extract the essentials
    java -jar dist/DanParse.jar ${TESTMODE} ${RAWFILE} ${OUTFILE}
    status=$(head -n 1 ${OUTFILE})
    echo "Test result: ${status}"

# restore original dir and remove any temp files created
exit_cleanup
exit 0

