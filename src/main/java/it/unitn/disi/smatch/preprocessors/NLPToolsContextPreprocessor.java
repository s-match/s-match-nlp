package it.unitn.disi.smatch.preprocessors;

import it.unitn.disi.nlptools.ILabelPipeline;
import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.data.Label;
import it.unitn.disi.smatch.data.ling.IAtomicConceptOfLabel;
import it.unitn.disi.smatch.data.ling.ISense;
import it.unitn.disi.smatch.data.trees.IContext;
import it.unitn.disi.smatch.data.trees.INode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Performs linguistic preprocessing using NLPTools, on errors falls back to heuristic-based one.
 *
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class NLPToolsContextPreprocessor implements IContextPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(NLPToolsContextPreprocessor.class);

    private final ILabelPipeline pipeline;
    private final DefaultContextPreprocessor dcp;
    // flag to output the label being translated in logs
    private final boolean debugLabels;

    // TODO thread safe
    private int fallbackCount;

    public NLPToolsContextPreprocessor(ILabelPipeline pipeline) {
        this.pipeline = pipeline;
        this.debugLabels = false;
        this.dcp = null;
    }

    public NLPToolsContextPreprocessor(ILabelPipeline pipeline, boolean debugLabels) {
        this.pipeline = pipeline;
        this.debugLabels = debugLabels;
        this.dcp = null;
    }

    public NLPToolsContextPreprocessor(ILabelPipeline pipeline, DefaultContextPreprocessor dcp, boolean debugLabels) {
        this.pipeline = pipeline;
        this.dcp = dcp;
        this.debugLabels = debugLabels;
    }

    public void preprocess(IContext context) throws ContextPreprocessorException {
        //go DFS, processing label-by-label, keeping path-to-root as context
        //process each text getting the formula

        int processedCount = 0;
        fallbackCount = 0;
        try {
            pipeline.beforeProcessing();
        } catch (PipelineComponentException e) {
            throw new ContextPreprocessorException(e.getMessage(), e);
        }

        List<INode> queue = new ArrayList<>();
        List<INode> pathToRoot = new ArrayList<>();
        List<ILabel> pathToRootPhrases = new ArrayList<>();
        queue.add(context.getRoot());

        while (!queue.isEmpty()) {
            INode currentNode = queue.remove(0);
            if (null == currentNode) {
                pathToRoot.remove(pathToRoot.size() - 1);
                pathToRootPhrases.remove(pathToRootPhrases.size() - 1);
            } else {
                ILabel currentPhrase;
                currentPhrase = processNode(currentNode, pathToRootPhrases);
                processedCount++;

                List<INode> children = currentNode.getChildrenList();
                if (0 < children.size()) {
                    queue.add(0, null);
                    pathToRoot.add(currentNode);
                    pathToRootPhrases.add(currentPhrase);
                }
                for (int i = children.size() - 1; i >= 0; i--) {
                    queue.add(0, children.get(i));
                }

            }
        }

        try {
            pipeline.afterProcessing();
        } catch (PipelineComponentException e) {
            throw new ContextPreprocessorException(e.getMessage(), e);
        }
        log.info("Processed nodes: " + processedCount + ", fallbacks: " + fallbackCount);
    }

    /**
     * Converts current node label into a formula using path to root as a context
     *
     * @param currentNode       a node to process
     * @param pathToRootPhrases phrases in the path to root
     * @return phrase instance for a current node label
     * @throws ContextPreprocessorException ContextPreprocessorException
     */
    private ILabel processNode(INode currentNode, List<ILabel> pathToRootPhrases) throws ContextPreprocessorException {
        if (debugLabels) {
            log.debug("preprocessing node: " + currentNode.getNodeData().getId() + ", label: " + currentNode.getNodeData().getName());
        }

        // reset old preprocessing
        currentNode.getNodeData().setcLabFormula("");
        currentNode.getNodeData().setcNodeFormula("");
        while (0 < currentNode.getNodeData().getACoLCount()) {
            currentNode.getNodeData().removeACoL(0);
        }

        String label = currentNode.getNodeData().getName();
        ILabel result = new Label(label);
        result.setContext(pathToRootPhrases);
        try {
            pipeline.process(result);

            //should contain only token indexes. including not recognized, but except closed class tokens.
            //something like
            // 1 & 2
            // 1 & (3 | 4)
            String formula = result.getFormula();
            currentNode.getNodeData().setIsPreprocessed(true);

            //create acols. one acol for each concept (meaningful) token
            //non-concept tokens should not make it up to a formula.
            String[] tokenIndexes = formula.split("[ ()&|~]");
            Set<String> indexes = new HashSet<>(Arrays.asList(tokenIndexes));
            List<IToken> tokens = result.getTokens();
            for (int i = 0; i < tokens.size(); i++) {
                IToken token = tokens.get(i);
                String tokenIdx = Integer.toString(i);
                if (indexes.contains(tokenIdx)) {
                    IAtomicConceptOfLabel acol = currentNode.getNodeData().createACoL();
                    acol.setId(i);
                    acol.setToken(token.getText());
                    acol.setLemma(token.getLemma());
                    for (ISense sense : token.getSenses()) {
                        acol.addSense(sense);
                    }
                    currentNode.getNodeData().addACoL(acol);
                }
            }

            //prepend all token references with node id
            formula = formula.replaceAll("(\\d+)", currentNode.getNodeData().getId() + ".$1");
            formula = formula.trim();
            //set it to the node
            currentNode.getNodeData().setcLabFormula(formula);
        } catch (PipelineComponentException e) {
            if (log.isWarnEnabled()) {
                log.warn("Falling back to heuristic parser for label (" + result.getText() + "): " + e.getMessage(), e);
                fallbackCount++;
                dcp.processNode(currentNode, new HashSet<String>());
            }
        }
        return result;
    }
}