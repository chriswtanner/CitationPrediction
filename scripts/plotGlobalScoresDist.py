#!/usr/bin/python
import random

scoresFile = '/Users/christanner/research/projects/CitationFinder/eval/scores_linkLDA_acl_5000.txt'
outputFile = '/Users/christanner/research/projects/CitationFinder/eval/plot_acl_5000.csv'
numLines = 12411585
numOutput = 10000
rate = float(numOutput) / float(numLines)
print rate

bout = open(outputFile,'w')

with open(scoresFile) as f:
    i=1
    for line in f:
        tokens = line.split()
        if (random.random() < rate):
            bout.write(str(i) + "," + str(tokens[2]) + "\n")
            i += 1

print "wrote " + str(i) + " lines"
bout.close()
