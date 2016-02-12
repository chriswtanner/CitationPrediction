#!/usr/bin/python

corpus = 'pubmed'

# input
contentFile = '/Users/christanner/research/libraries/text-link-code/data/' + corpus + '/' + corpus + '.content'

# output 
docFile = open('/Users/christanner/research/projects/CitationFinder/eval/' + corpus + '-doc.txt', 'w')
malletFile = open('/Users/christanner/research/projects/CitationFinder/eval/' + corpus + '-mallet.txt', 'w')

docIDs = []
with open(contentFile) as f:
    for line in f:
        tokens = line.split()
        docID = tokens[0]
        docIDs.append(docID)

        out = ""
        for i in range(1, (len(tokens)/2)):
            wordNum = tokens[i*2 - 1]
            wordCount = tokens[i*2]
            
            for j in range(0,int(wordCount)):
                out += (" " + wordNum) 

        malletFile.write(str(docID) + " " + str(docID) + out + "\n")

malletFile.close()

docFile.write("size of corpus: " + str(len(docIDs)) + "\n\n")
docFile.write("filename\treport\tsource:\n")
for docID in docIDs:
    docFile.write(docID + "\t*\n")
docFile.close()
