package cc.mallet.topics.tree;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import cc.mallet.topics.tree.TreeTopicSampler.DocData;
import cc.mallet.util.Randoms;

public class TreeTopicSamplerFast extends TreeTopicSampler {
	
	public TreeTopicSamplerFast (int numberOfTopics, double alphaSum, int seed) {
		super(numberOfTopics, alphaSum, seed);
		this.topics = new TreeTopicModelFast(this.numTopics, this.random);
	}
	
	public void sampleDoc(int doc_id){
		DocData doc = this.data.get(doc_id);
		//System.out.println("doc " + doc_id);
		
		for(int ii = 0; ii < doc.tokens.size(); ii++) {	
			//int word = doc.tokens.getIndexAtPosition(ii);
			int word = doc.tokens.get(ii);
			
			this.changeTopic(doc_id, ii, word, -1, -1);
			
			double smoothing_mass = this.topics.computeTermSmoothing(this.alpha, word);
			double topic_beta_mass = this.topics.computeTermTopicBeta(doc.topicCounts, word);
			
			HIntIntDoubleHashMap topic_term_score = new HIntIntDoubleHashMap();
			double topic_term_mass = this.topics.computeTopicTerm(this.alpha, doc.topicCounts, word, topic_term_score);
			
			double norm = smoothing_mass + topic_beta_mass + topic_term_mass;
			double sample = this.random.nextDouble();
			//double sample = 0.5;
			sample *= norm;

			int new_topic = -1;
			int new_path = -1;
			
			int[] paths = this.topics.getWordPathIndexSet(word);
			
			if (sample < smoothing_mass) {
				for (int tt = 0; tt < this.numTopics; tt++) {
					for (int pp : paths) {
						double val = alpha[tt] * this.topics.getPathPrior(word, pp);
						val /= this.topics.getNormalizer(tt, pp);
						sample -= val;
						if (sample <= 0.0) {
							new_topic = tt;
							new_path = pp;
							break;
						}
					}
					if (new_topic >= 0) {
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling smoothing!");
			} else {
				sample -= smoothing_mass;
			}
			
			if (new_topic < 0 && sample < topic_beta_mass) {
				for(int tt : doc.topicCounts.keys()) {
					for (int pp : paths) {
						double val = doc.topicCounts.get(tt) * this.topics.getPathPrior(word, pp);
						val /= this.topics.getNormalizer(tt, pp);
						sample -= val;
						if (sample <= 0.0) {
							new_topic = tt;
							new_path = pp;
							break;
						}
					}
					if (new_topic >= 0) {
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling topic beta!");
			} else {
				sample -= topic_beta_mass;
			}
			
			if (new_topic < 0) {
				int[] topic_set = topic_term_score.getKey1Set();
				for (int tt : topic_set) {
					int[] path_set = topic_term_score.get(tt).keys();
					for (int pp : path_set) {
						double val = topic_term_score.get(tt, pp);
						//System.out.println(tt + " " + pp + " " + val);
						sample -= val;
						if (sample <= 0.0) {
							new_topic = tt;
							new_path = pp;
							break;
						}
					}
					if (new_topic >= 0) {
						break;
					}
				}
				myAssert((new_topic >= 0 && new_topic < numTopics), "something wrong in sampling topic term!");
			}
			
			this.changeTopic(doc_id, ii, word, new_topic, new_path);
		}
	}
	
	/////////////////////////////
	
	public double callComputeTermTopicBeta(TIntIntHashMap topic_counts, int word) {
		return this.topics.computeTermTopicBeta(topic_counts, word);
	}
	
	public double callComputeTermSmoothing(int word) {
		return this.topics.computeTermSmoothing(this.alpha, word);
	}

	public double computeTopicSmoothTest(int word) {
		double smooth = 0.0;
		int[] paths = this.topics.getWordPathIndexSet(word);
		for(int tt = 0; tt < this.numTopics; tt++) {
			double topic_alpha = alpha[tt];
			for (int pp = 0; pp < paths.length; pp++) {
				int path_index = paths[pp];
				
				TIntArrayList path_nodes = this.topics.wordPaths.get(word, path_index);
				TopicTreeWalk tw = this.topics.traversals.get(tt);
				
				double tmp = 1.0;
				for(int ii = 0; ii < path_nodes.size()-1; ii++) {
					int parent = path_nodes.get(ii);
					int child = path_nodes.get(ii+1);
					tmp *= this.topics.beta.get(parent, child);
					tmp /= this.topics.betaSum.get(parent) + tw.getNodeCount(parent);
				}
				tmp *= topic_alpha;
				smooth += tmp;
			}
		}
		return smooth;
	}
	
	public double computeTopicTermBetaTest(TIntIntHashMap local_topic_counts, int word) {
		double topictermbeta = 0.0;
		int[] paths = this.topics.getWordPathIndexSet(word);
		for(int tt = 0; tt < this.numTopics; tt++) {
			int topic_count = local_topic_counts.get(tt);
			for (int pp = 0; pp < paths.length; pp++) {
				int path_index = paths[pp];
				
				TIntArrayList path_nodes = this.topics.wordPaths.get(word, path_index);
				TopicTreeWalk tw = this.topics.traversals.get(tt);
								
				double tmp = 1.0;
				for(int ii = 0; ii < path_nodes.size()-1; ii++) {
					int parent = path_nodes.get(ii);
					int child = path_nodes.get(ii+1);
					tmp *= this.topics.beta.get(parent, child);
					tmp /= this.topics.betaSum.get(parent) + tw.getNodeCount(parent);
				}
				tmp *= topic_count;
				
				topictermbeta += tmp;
			}
		}
		return topictermbeta;
	}
	
	public double computeTopicTermScoreTest(double[] alpha, TIntIntHashMap local_topic_counts, int word, HIntIntDoubleHashMap dict) {
		double termscore = 0.0;
		int[] paths = this.topics.getWordPathIndexSet(word);
		for(int tt = 0; tt < this.numTopics; tt++) {
			double topic_alpha = alpha[tt];
			int topic_count = local_topic_counts.get(tt);
			for (int pp = 0; pp < paths.length; pp++) {
				int path_index = paths[pp];
				
				TIntArrayList path_nodes = this.topics.wordPaths.get(word, path_index);
				TopicTreeWalk tw = this.topics.traversals.get(tt);
				
				double val = 1.0;
				double tmp = 1.0;
				double normalizer = 1.0;
				for(int ii = 0; ii < path_nodes.size()-1; ii++) {
					int parent = path_nodes.get(ii);
					int child = path_nodes.get(ii+1);
					val *= this.topics.beta.get(parent, child) + tw.getCount(parent, child);
					tmp *= this.topics.beta.get(parent, child);
					normalizer *= this.topics.betaSum.get(parent) + tw.getNodeCount(parent);
				}
				val -= tmp;
				val *= (topic_alpha + topic_count);
				val /= normalizer;
				
				dict.put(tt, path_index, val);
				termscore += val;
			}
		}
		return termscore;
	}
	
	public double computeTopicTermTest(double[] alpha, TIntIntHashMap local_topic_counts, int word, HIntIntDoubleHashMap dict) {
		double norm = 0.0;
		int[] paths = this.topics.getWordPathIndexSet(word);
		for(int tt = 0; tt < this.numTopics; tt++) {
			double topic_alpha = alpha[tt];
			int topic_count = local_topic_counts.get(tt);
			for (int pp = 0; pp < paths.length; pp++) {
				int path_index = paths[pp];
				
				TIntArrayList path_nodes = this.topics.wordPaths.get(word, path_index);
				TopicTreeWalk tw = this.topics.traversals.get(tt);
				
				double smooth = 1.0;
				for(int ii = 0; ii < path_nodes.size()-1; ii++) {
					int parent = path_nodes.get(ii);
					int child = path_nodes.get(ii+1);
					smooth *= this.topics.beta.get(parent, child);
					smooth /= this.topics.betaSum.get(parent) + tw.getNodeCount(parent);
				}
				smooth *= topic_alpha;
				
				double topicterm = 1.0;
				for(int ii = 0; ii < path_nodes.size()-1; ii++) {
					int parent = path_nodes.get(ii);
					int child = path_nodes.get(ii+1);
					topicterm *= this.topics.beta.get(parent, child);
					topicterm /= this.topics.betaSum.get(parent) + tw.getNodeCount(parent);
				}
				topicterm *= topic_count;
				
				double termscore = 1.0;
				double tmp = 1.0;
				double normalizer = 1.0;
				for(int ii = 0; ii < path_nodes.size()-1; ii++) {
					int parent = path_nodes.get(ii);
					int child = path_nodes.get(ii+1);
					termscore *= this.topics.beta.get(parent, child) + tw.getCount(parent, child);
					tmp *= this.topics.beta.get(parent, child);
					normalizer *= this.topics.betaSum.get(parent) + tw.getNodeCount(parent);
				}
				termscore -= tmp;
				termscore *= (topic_alpha + topic_count);
				termscore /= normalizer;
				
				double val = smooth + topicterm + termscore;
				dict.put(tt, path_index, val);
				norm += val;
				System.out.println("Fast Topic " + tt + " " + smooth + " " + topicterm + " " + termscore + " " + tmp + " " + topic_alpha + " " + topic_count + " " + termscore);
			}
		}
		return norm;
	}
}
