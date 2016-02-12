#!/usr/bin/python

def main():

    corpus = 'acl_4002'
    trainFile = '/Users/christanner/research/projects/CitationFinder/eval/' + corpus + '.training'
    testFile = '/Users/christanner/research/projects/CitationFinder/eval/' + corpus + '.testing'

    # reads in training's golden, so that we know to exclude these from our predicted links
    training = {}
    # token[0] = source; token[1] = report
    with open(trainFile) as f:
        for line in f:
            tokens = line.split()
            training.setdefault(tokens[0],list()).append(tokens[1])
    f.close()

    # reads in testing's golden
    testing = {}
    with open(testFile) as f:
        for line in f:
            tokens = line.split()
            testing.setdefault(tokens[0],list()).append(tokens[1])
    f.close()

    print "training keys: " + str(len(training.keys()))
    numMissing = 0
    numTotal = 0
    for source in testing:
        numReportsInTraining = 0
        if (source in training.keys()):
            numReportsInTraining = len(training[source])
        else:
            numMissing += 1
        print str(numReportsInTraining)
        numTotal += 1

    print str(numMissing) + " ; total: " + str(numTotal)
    
main()
