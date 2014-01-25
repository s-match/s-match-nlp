package it.unitn.disi.nlptools.components.lemmatizers;

import it.unitn.disi.common.components.ConfigurableException;
import it.unitn.disi.common.components.ConfigurationKeyMissingException;
import it.unitn.disi.nlptools.components.PipelineComponentException;
import it.unitn.disi.nlptools.data.ILabel;
import it.unitn.disi.nlptools.data.IToken;
import it.unitn.disi.nlptools.pipelines.LabelPipelineComponent;
import it.unitn.disi.smatch.oracles.ILinguisticOracle;
import it.unitn.disi.smatch.oracles.LinguisticOracleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

/**
 * @author <a rel="author" href="http://autayeu.com/">Aliaksandr Autayeu</a>
 */
public class LinguisticOracleLemmatizer extends LabelPipelineComponent {

    private static final Logger log = LoggerFactory.getLogger(LinguisticOracleLemmatizer.class);

    private static final String ORACLE_KEY = "oracle";
    private ILinguisticOracle oracle;

    public void process(ILabel instance) throws PipelineComponentException {
        for (IToken token : instance.getTokens()) {
            try {
                List<String> lemmas = oracle.getBaseForms(token.getText());
                if (0 < lemmas.size()) {
                    token.setLemma(lemmas.get(0));//lemma is supposed to be that of an "active" sense
                }
            } catch (LinguisticOracleException e) {
                throw new PipelineComponentException(e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean setProperties(Properties newProperties) throws ConfigurableException {
        if (log.isInfoEnabled()) {
            log.info("Loading configuration...");
        }
        Properties oldProperties = new Properties();
        oldProperties.putAll(properties);
        boolean result = super.setProperties(newProperties);
        if (result) {
            if (newProperties.containsKey(ORACLE_KEY)) {
                oracle = (ILinguisticOracle) configureComponent(oracle, oldProperties, newProperties, "linguistic oracle", ORACLE_KEY, ILinguisticOracle.class);
            } else {
                throw new ConfigurationKeyMissingException(ORACLE_KEY);
            }
        }
        return result;
    }
}
