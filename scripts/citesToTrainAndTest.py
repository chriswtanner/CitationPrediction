#!/usr/bin/python
import random

corpus = 'pubmed'
trainingLines = 0
testingLines = 0
trainingRatio = 0.9
input = '/Users/christanner/research/libraries/text-link-code/data/' + corpus + '/' + corpus + '.cites'
outputTraining = '/Users/christanner/research/projects/CitationFinder/eval/' + corpus + '.training'
outputTesting = '/Users/christanner/research/projects/CitationFinder/eval/' + corpus + '.testing'

trainOut = open(outputTraining, 'w')
testOut = open(outputTesting, 'w')

with open(input) as f:
    for line in f:
        tokens = line.split()
        newline = tokens[0] + " " + tokens[1] + "\n"
        if (random.random() <= trainingRatio):
            trainOut.write(newline)
            trainingLines += 1
        else:
            testOut.write(newline)
            testingLines += 1

trainOut.close()
testOut.close()

total = trainingLines + testingLines
print "training lines: " + str(trainingLines)
print "testing lines: " + str(testingLines)
print "total lines: " + str(total)
print "training %: " +  str(1.0*trainingLines / total)
