# import modules and set up logging
#import sys
#sys.path.append("/Users/christanner/research/libraries/gensim-0.10.0")

from gensim.models import word2vec
import logging
logging.basicConfig(format='%(asctime)s : %(levelname)s : %(message)s', level=logging.INFO)

# load up unzipped corpus from http://mattmahoney.net/dc/text8.zip
print "(driver) calling Text8Corpus()"
sentences = word2vec.Text8Corpus('/Users/christanner/research/libraries/word2vec/text8_small')

#print "# sentences: " + str(len(sentences.sentence))

# train the skip-gram model; default window=5
print "(driver) calling Word2Vec()"
model = word2vec.Word2Vec(sentences, size=200)

# ... and some hours later... just as advertised...
model.most_similar(positive=['woman', 'king'], negative=['man'], topn=1)
#[('queen', 0.5359965)]
 
# pickle the entire model to disk, so we can load&resume training later
print "(driver) calling .save()"
model.save('/Users/christanner/research/libraries/word2vec/text8.model')

# store the learned weights, in a format the original C tool understands
print "(Driver) calling .save_word2vec_format()"
model.save_word2vec_format('/Users/christanner/research/libraries/word2vec/text8.model.bin', binary=True)

# or, import word weights created by the (faster) C word2vec
# this way, you can switch between the C/Python toolkits easily
#model = word2vec.Word2Vec.load_word2vec_format('/Users/christanner/research/libraries/word2vec/vectors.bin', binary=True)
 
# "boy" is to "father" as "girl" is to ...?
print "(Driver) calling .most_similar()"
model.most_similar(['girl', 'father'], ['boy'], topn=3)

print "most sim"

#[('mother', 0.61849487), ('wife', 0.57972813), ('daughter', 0.56296098)]
more_examples = ["he his she", "going went being"]

#for example in more_examples:
#    a, b, x = example.split()
#    predicted = model.most_similar([x, b], [a])[0][0]
#    print "'%s' is to '%s' as '%s' is to '%s'" % (a, b, x, predicted)

# 'he' is to 'his' as 'she' is to 'her'
# 'big' is to 'bigger' as 'bad' is to 'worse'
# 'going' is to 'went' as 'being' is to 'was'
 
# which word doesn't go with the others?
#model.doesnt_match("dog cat he".split())
#model.doesnt_match("breakfast cereal dinner lunch".split())

#'cereal'
