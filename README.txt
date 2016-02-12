--------
Purpose:
--------
automatically determines which sentences in a given document cite other documents; further, attempts to determine which documents are the correct citation.


-----------
How to run:
-----------
1. prepare a corpus:

   java ACLDualConverter

   input:
	- ACL anthology (aan/papers_text/)
	- ACL network file (aan/release/2013/acl.txt)
	- ACL metadata file (aan/release/2013/acl-metadata.txt)
	- stopwords file

   output:
	- mallet-input.txt (used for running mallet's LDA)
	- documents in a cleaned form (including sentence citations for reports)
	- docsLegend (lists all docs and signifies which are reports and/or sources)
	- referential (lists all sources for every report, including the few which don't have the tagged sentences which cite them)

-----------
2. optionally run LDA if i care to use it for my main citation engine

   java -Xmx8g LDADriver

   input:
	- mallet-input.txt
	- stopwords (for mallet, but i think it's redundant since mallet-input is already filtered on stopwords)

   output:
	- ldaObject (serialized format) which stores P(W|Z), P(Z|D), wordToID, IDToWord maps
-----------
3. citation engine

    java CiteEvaluator

    input:
	- ldaObject
	- documents in a cleaned form (from ACLDualConverter)
	- docsLegend (from ACLDualConverter)
	- referential (from ACLDualConverter)
	- ACL metadata file (aan/release/2013/acl-metadata.txt) for displaying titles of our source predictions

    output:
        - (optionally) outputs the source or sentence predictions to a dir
-----------
4. svm helper

 overview: used for the report-to-source prediction task, which uses libsvm
           as the main way of testing out performance.  but, in order to use libsvm,
           we must first do (a) below.
           this uses this class (SVMHelper), and requires having already run
           an LDA run, and its output goes into libsvm.  after having run libsvm,
           **** MAKE SURE TO COPY the .prediction file from the libsvm dir TO
           the place location that is specified in the global var in SVMHelper ***
           once you’ve done this, you can then run (b) to evaluate either SVM
           (which uses LDA + 3 features) OR LDA by itself.


 - 2 ways to run it:
 (a) prepares data so libsvm can run; set both evaluateSVM/LDA = false
 (b) evaluates libsvm’s results; set 1 of evaluateSVM/LDA = true
 
    java SVMHelper

    input:
	- ldaObject
	- documents in a cleaned form (from ACLDualConverter)
	- docsLegend (from ACLDualConverter)
	- referential (from ACLDualConverter)
	- ACL metadata file (aan/release/2013/acl-metadata.txt) for displaying titles of our source predictions

 (a)
    output:
        - training and testing files for LibSVM

 THEN RUN LIBSVM:

    ./svm-scale -l -1 -u 1 -s range1 ../../projects/CitationFinder/output/svm_training_features.txt > training.scale
    ./svm-scale -r range1 ../../projects/CitationFinder/output/svm_testing_features.txt > testing.scale
    python tools/subset.py training.scale 20000 training_sub.scale
    python tools/grid.py training_sub.scale
    ./svm-train -b 1 -c 32768 -g 0.5 -m 12000 training_sub.scale 
    ./svm-predict -b 1 testing.scale training_sub.scale.model testing.predictions

   OR AS 1 COMMAND:
   ./svm-scale -l -1 -u 1 -s range1 ../../projects/CitationFinder/output/svm_training_features.txt > training.scale && ./svm-scale -r range1 ../../projects/CitationFinder/output/svm_testing_features.txt > testing.scale && python tools/subset.py training.scale 20000 training_sub.scale && ./svm-train -b 1 -c 32768 -g 0.5 -m 12000 training_sub.scale && ./svm-predict -b 1 testing.scale training_sub.scale.model testing.predictions

 (b) output:
         - recall scores and optional analysis files


-----------
5. ACLMasterSplitter.java

converts our already-curated ACL corpus into something that (1) PMTLM, and (2) our new CE can use

input:
 - big .mallet file (from ACL’s corpus), which is generated from ACLDualConverter.java
 - referential file (from ACL’s corpus)
 - matadors (from ACL’s corpus) because we need to know author information for every report and source
 - # of docs that we wish to have in our corpus

output:
 - .training (for PMTLM)
 - .testing (for SVM/evaluation)
 - .content (for PMTLM)
 - .mallet (for TopicModeller)

-----------
6. ACLMasterSplitter2.java

instead of running ACLDualConverter.java then ACLMasterSplitter, this version combines both and ignores the 
stuff within ACLDualConverter.java that actually does the ‘dual’ part (i.e., the stuff that determines and writes
to disk the sentences that do the citing)




-----------
SCRIPTS DIR — used for pre-processing files and evaluating results outside of my system
-----------
1. citesToTrainAndTest.py

convert text-link-code’s data to the right format so that it runs on 90% of the data during ‘training,’ and the remaining 10% will be used for evaluation (via evaluatePMTLM.py)

run: python citesToTrainAndTest.py

note: adjust input/output filenames at the beginning of the script

[CAN NOW RUN ./main PMTLM(-DC) which outputs thetaMatrix, omegaMatrix, dcParams files
-----------
2. evaluatePMTLM.py

evaluates how well PMTLM(-DC) does on any of the passed-in datasets - - the gold labels it’s after are the ones in ‘training,’ and it ignores the positive links in ‘training,’ since these golden links have already been trained on

NOTE: be sure to flip the flag for ‘PMTLM_Vanilla’ appropriate; false = the -DC model
outputs:
- g1.csv = recall vs precision (x,y style) data
- g2.csv = false pos vs true pos (aka recall) (x,y style) data

input:
 - theta/omega files from PMTLM
 - .training and .testing file from ‘citesToTrainAndTest’

-----------
3. convertDataToCitationEngine.py

converts data from the format of PMTLM (i.e., boolean-valued vector of word counts, and ‘backward’ .citation link file to the files i need for CitationEngine.java

input:
 - .content
 - .cites

output:
 - referential file (report and sources again, but CitationEngine needs a boolean value (i.e., pmtlmData = true or false)
   to represent if we should ahead of time preload our candidate ‘sources’.  if true, then our sources are
   EVERY doc except the report itself and the golden sources that were already in .train (so i need to load this in CitationEngine, too)
 - mallet-input format
 - docLegend which simply lists if each doc is a report and/or source


