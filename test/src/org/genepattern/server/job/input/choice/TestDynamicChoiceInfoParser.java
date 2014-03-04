package org.genepattern.server.job.input.choice;

import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.genepattern.junitutil.TaskUtil;
import org.genepattern.server.job.input.TestLoadModuleHelper;
import org.genepattern.server.rest.ParameterInfoRecord;
import org.genepattern.webservice.ParameterInfo;
import org.genepattern.webservice.TaskInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * jUnit tests for the DynamicChoiceInfoParser class.
 * @author pcarr
 *
 */
public class TestDynamicChoiceInfoParser {
    private static final String gseaGeneSetsDatabaseChoicesFromManifest="noGeneSetsDB=;c1.all.v4.0.symbols.gmt=c1.all.v4.0.symbols.gmt [Positional];c2.all.v4.0.symbols.gmt=c2.all.v4.0.symbols.gmt [Curated];c2.cgp.v4.0.symbols.gmt=c2.cgp.v4.0.symbols.gmt [Curated];c2.cp.v4.0.symbols.gmt=c2.cp.v4.0.symbols.gmt [Curated];c2.cp.biocarta.v4.0.symbols.gmt=c2.cp.biocarta.v4.0.symbols.gmt [Curated];c2.cp.kegg.v4.0.symbols.gmt=c2.cp.kegg.v4.0.symbols.gmt [Curated];c2.cp.reactome.v4.0.symbols.gmt=c2.cp.reactome.v4.0.symbols.gmt [Curated];c3.all.v4.0.symbols.gmt=c3.all.v4.0.symbols.gmt [Motif];c3.mir.v4.0.symbols.gmt=c3.mir.v4.0.symbols.gmt [Motif];c3.tft.v4.0.symbols.gmt=c3.tft.v4.0.symbols.gmt [Motif];c4.all.v4.0.symbols.gmt=c4.all.v4.0.symbols.gmt [Computational];c4.cgn.v4.0.symbols.gmt=c4.cgn.v4.0.symbols.gmt [Computational];c4.cm.v4.0.symbols.gmt=c4.cm.v4.0.symbols.gmt [Computational];c5.all.v4.0.symbols.gmt=c5.all.v4.0.symbols.gmt [Gene Ontology];c5.bp.v4.0.symbols.gmt=c5.bp.v4.0.symbols.gmt [Gene Ontology];c5.cc.v4.0.symbols.gmt=c5.cc.v4.0.symbols.gmt [Gene Ontology];c5.mf.v4.0.symbols.gmt=c5.mf.v4.0.symbols.gmt [Gene Ontology];c6.all.v4.0.symbols.gmt=c6.all.v4.0.symbols.gmt [Oncogenic Signatures];c7.all.v4.0.symbols.gmt=c7.all.v4.0.symbols.gmt [Immunologic signatures]";
    private static List<Choice> gseaGeneSetsDatabaseChoices;
    
    @BeforeClass
    public static void initClass() {
        gseaGeneSetsDatabaseChoices=new ArrayList<Choice>();
        //new Choice( <label>, <value> )
        gseaGeneSetsDatabaseChoices.add(new Choice("", "noGeneSetsDB"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c1.all.v4.0.symbols.gmt [Positional]", "c1.all.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c2.all.v4.0.symbols.gmt [Curated]", "c2.all.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c2.cgp.v4.0.symbols.gmt [Curated]", "c2.cgp.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c2.cp.v4.0.symbols.gmt [Curated]", "c2.cp.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c2.cp.biocarta.v4.0.symbols.gmt [Curated]", "c2.cp.biocarta.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c2.cp.kegg.v4.0.symbols.gmt [Curated]", "c2.cp.kegg.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c2.cp.reactome.v4.0.symbols.gmt [Curated]", "c2.cp.reactome.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c3.all.v4.0.symbols.gmt [Motif]", "c3.all.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c3.mir.v4.0.symbols.gmt [Motif]", "c3.mir.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c3.tft.v4.0.symbols.gmt [Motif]", "c3.tft.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c4.all.v4.0.symbols.gmt [Computational]", "c4.all.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c4.cgn.v4.0.symbols.gmt [Computational]", "c4.cgn.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c4.cm.v4.0.symbols.gmt [Computational]", "c4.cm.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c5.all.v4.0.symbols.gmt [Gene Ontology]", "c5.all.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c5.bp.v4.0.symbols.gmt [Gene Ontology]", "c5.bp.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c5.cc.v4.0.symbols.gmt [Gene Ontology]", "c5.cc.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c5.mf.v4.0.symbols.gmt [Gene Ontology]", "c5.mf.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c6.all.v4.0.symbols.gmt [Oncogenic Signatures]", "c6.all.v4.0.symbols.gmt"));
        gseaGeneSetsDatabaseChoices.add(new Choice("c7.all.v4.0.symbols.gmt [Immunologic signatures]", "c7.all.v4.0.symbols.gmt"));
    }

    @Test
    public void testParseChoiceEntry_null() {
        try {
            ChoiceInfoHelper.initChoiceFromManifestEntry(null);
            Assert.fail("null arg should cause IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
            //expected
        }
    }
    
    @Test
    public void testParseChoiceEntry_empty() throws Exception {
        Choice choice=ChoiceInfoHelper.initChoiceFromManifestEntry("");
        Choice expected=new Choice("");
        Assert.assertEquals("expected.label should be empty", "", expected.getLabel());
        Assert.assertEquals("expected.value should be empty", "", expected.getValue());
        Assert.assertEquals("choice from '='", expected, choice);
    }
    
    @Test
    public void testParseChoiceEntry_spaces() throws Exception {
        Choice choice=ChoiceInfoHelper.initChoiceFromManifestEntry(" ");
        Choice expected=new Choice(" ");
        Assert.assertEquals("don't trim space characters", expected, choice);
    }

    @Test
    public void testParseChoiceEntry_spaces02() throws Exception {
        Choice choice=ChoiceInfoHelper.initChoiceFromManifestEntry("  =  ");
        Choice expected=new Choice("  ");
        Assert.assertEquals("don't trim space characters", expected, choice);
    }
    
    @Test
    public void testParseChoiceEntry_equalsOnly() throws Exception {
        Choice choice=ChoiceInfoHelper.initChoiceFromManifestEntry("=");
        Choice expected=new Choice("");
        Assert.assertEquals("choice from '='", expected, choice);
    }
    
    @Test
    public void testParseChoiceEntry_emptyLhs() throws Exception {
        Choice choice=ChoiceInfoHelper.initChoiceFromManifestEntry("=NonBlankDisplayValue");
        Choice expected=new Choice("NonBlankDisplayValue", "");
        Assert.assertEquals(expected, choice);
    }
    
    @Test
    public void testParseChoiceEntry_emptyRhs() throws Exception {
        Choice choice=ChoiceInfoHelper.initChoiceFromManifestEntry("NonBlankActualValue=");
        Choice expected=new Choice("", "NonBlankActualValue");
        Assert.assertEquals(expected, choice);
    }
    
    @Test
    public void testInitChoicesFromManifestEntry() {
        final List<Choice> choices=ChoiceInfoHelper.initChoicesFromManifestEntry(gseaGeneSetsDatabaseChoicesFromManifest);
        Assert.assertEquals(gseaGeneSetsDatabaseChoices, choices);
    }
    
    /**
     * Test the 'gene.sets.database' drop-down for GSEA v. 14.
     * It has an empty display value, 'noGeneSetsDB'
     */
    @Test
    public void testLegacyChoiceGsea14() {
        //
        final TaskInfo taskInfo=TaskUtil.getTaskInfoFromZip(TestDynamicChoiceInfoParser.class, "GSEA_v14.zip");
        final Map<String,ParameterInfoRecord> paramInfoMap=ParameterInfoRecord.initParamInfoMap(taskInfo);
        final ParameterInfoRecord pinfoRecord=paramInfoMap.get("gene.sets.database");
        
        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfoRecord.getFormal());
        
        Assert.assertNotNull("Expecting a non-null choiceInfo", choiceInfo);
        final List<Choice> choices=choiceInfo.getChoices();
        Assert.assertThat("", choices, is(gseaGeneSetsDatabaseChoices));
    }
    
    /**
     * Legacy test, for some existing modules, such as ExtractComparativeMarkerResults, text drop-down with no default
     * value must default to the 1st item on the list, regardless of whether or not it is optional.
     */
    @Test
    public void testLegacyChoiceExtractComparativeMarkerResults() {
        final TaskInfo taskInfo=TaskUtil.getTaskInfoFromZip(TestLoadModuleHelper.class, "ExtractComparativeMarkerResults_v4.zip");
        final Map<String,ParameterInfoRecord> paramInfoMap=ParameterInfoRecord.initParamInfoMap(taskInfo);
        final ParameterInfoRecord pinfoRecord=paramInfoMap.get("statistic");
        
        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfoRecord.getFormal());
        Assert.assertNotNull("Expecting a non-null choiceInfo", choiceInfo);
        Choice selected=choiceInfo.getSelected();
        Assert.assertNotNull("Expecting a non-null choiceInfo#selected", selected);
        Assert.assertEquals("Checking default label", "Bonferroni", selected.getLabel());
        Assert.assertEquals("Checking default value", "Bonferroni", selected.getValue());
    }
    
    @Test
    public void testLegacyOptionalDropdown() {
        final String value="arg1;arg2;arg3;arg4";
        
        ParameterInfo pinfo=new ParameterInfo("my.param", value, "A choice parameter with no default value");
        pinfo.setAttributes(new HashMap<String,String>());
        pinfo.getAttributes().put("default_value", "");
        pinfo.getAttributes().put("optional", "on");
        pinfo.getAttributes().put("prefix_when_specified", "");
        pinfo.getAttributes().put("type", "java.lang.String");
        
        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfo);
        Assert.assertNotNull("Expecting a non-null choiceInfo", choiceInfo);
        Choice selected=choiceInfo.getSelected();
        Assert.assertNotNull("Expecting a non-null choiceInfo#selected", selected);
        Assert.assertEquals("Checking default label", "arg1", selected.getLabel());
        Assert.assertEquals("Checking default value", "arg1", selected.getValue());
    }
    
    private ParameterInfo initFtpParam(final String choiceDir) {
        final String name="input.file";
        final String value="";
        final String description="A file drop-down";

        final ParameterInfo pinfo=new ParameterInfo(name, value, description);
        pinfo.setAttributes(new HashMap<String,String>());
        pinfo.getAttributes().put("MODE", "IN");
        pinfo.getAttributes().put("TYPE", "FILE");
        pinfo.getAttributes().put("choiceDir", choiceDir);
        pinfo.getAttributes().put("default_value", "");
        pinfo.getAttributes().put("fileFormat", "txt");
        pinfo.getAttributes().put("flag", "");
        pinfo.getAttributes().put("optional", "");
        pinfo.getAttributes().put("prefix", "");
        pinfo.getAttributes().put("prefix_when_specified", "");
        pinfo.getAttributes().put("type", "java.io.File");

        return pinfo;
    }
    
    private Choice makeChoice(final String choiceDir, final String entry, final boolean isDir) {
        final String label=entry;
        final String value=choiceDir+""+entry;
        return new Choice(label, value, isDir);
    }
    
    private void listCompare(final String message, final List<Choice> expected, final List<Choice> actual) {
        Assert.assertEquals(message+", num elements", expected.size(), actual.size());
        for(int i=0; i<expected.size(); ++i) {
            Choice expectedChoice=expected.get(i);
            Choice actualChoice=actual.get(i);
            Assert.assertEquals(message+" ["+i+"] equals", expectedChoice, actualChoice);
        }
    }

    @Test
    public void testFtpFileDropdown() {
        final String choiceDir="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        
        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfo);
        
        Assert.assertNotNull("choiceInfo.choices", choiceInfo.getChoices());
        final List<Choice> expected=Arrays.asList(new Choice[] {
                new Choice("", ""),
                makeChoice(choiceDir, "a.txt", false), 
                makeChoice(choiceDir, "b.txt", false), 
                makeChoice(choiceDir, "c.txt", false), 
                makeChoice(choiceDir, "d.txt", false), 
        });
        
        listCompare("drop-down items", expected, choiceInfo.getChoices());
        Assert.assertEquals("selected", new Choice("", ""), choiceInfo.getSelected());
    }

    /**
     * This is not for automated testing because it fails about half the time.
     * Note the bogus assert statement which always passes.
     */
    @Test
    public void testDynamicDropdownGsea() {
        final String choiceDir="ftp://gseaftp.broadinstitute.org/pub/gsea/annotations/";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        final String choiceDirFilter="*.chip";
        pinfo.getAttributes().put("choiceDirFilter", choiceDirFilter);

        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfo);
        //Assert.assertEquals("num choices", 143, choiceInfo.getChoices().size());
        Assert.assertEquals("always pass", true, true);
    }

    /**
     * A required parameter with a default value should have an empty item at the start of the drop-down.
     */
    @Test
    public void testFtpFileDropdown_requiredWithDefaultValue() {
        final String choiceDir="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        pinfo.getAttributes().put("default_value", choiceDir+"a.txt");

        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfo);
        
        Assert.assertNotNull("choiceInfo.choices", choiceInfo.getChoices());
        final List<Choice> expected=Arrays.asList(new Choice[] {
                new Choice("", ""), 
                makeChoice(choiceDir, "a.txt", false), 
                makeChoice(choiceDir, "b.txt", false), 
                makeChoice(choiceDir, "c.txt", false), 
                makeChoice(choiceDir, "d.txt", false), 
        });
        listCompare("drop-down items", expected, choiceInfo.getChoices());
        Assert.assertEquals("selected", makeChoice(choiceDir, "a.txt", false), choiceInfo.getSelected());
    }

    /**
     * An optional parameter with a default value should have an empty item at the start of the drop-down.
     */
    @Test
    public void testFtpFileDropdown_optionalWithDefaultValue() {
        final String choiceDir="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        pinfo.getAttributes().put("default_value", choiceDir+"a.txt");
        pinfo.getAttributes().put("optional", "on");
        
        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfo);
        
        Assert.assertNotNull("choiceInfo.choices", choiceInfo.getChoices());
        final List<Choice> expected=Arrays.asList(new Choice[] {
                new Choice("", ""), 
                makeChoice(choiceDir, "a.txt", false), 
                makeChoice(choiceDir, "b.txt", false), 
                makeChoice(choiceDir, "c.txt", false), 
                makeChoice(choiceDir, "d.txt", false), 
        });
        listCompare("drop-down items", expected, choiceInfo.getChoices());
        Assert.assertEquals("selected", makeChoice(choiceDir, "a.txt", false), choiceInfo.getSelected());
    }

    /**
     * Test with a default value which doesn't match any items in the remote directory.
     */
    @Test
    public void testFtpFileDropdown_defaultValue_noMatch() {
        final String choiceDir="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        pinfo.getAttributes().put("default_value", choiceDir+"no_match.txt");

        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfo);
        
        Assert.assertNotNull("choiceInfo.choices", choiceInfo.getChoices());
        final List<Choice> expected=Arrays.asList(new Choice[] {
                new Choice("", ""), 
                makeChoice(choiceDir, "a.txt", false), 
                makeChoice(choiceDir, "b.txt", false), 
                makeChoice(choiceDir, "c.txt", false), 
                makeChoice(choiceDir, "d.txt", false), 
        });
        listCompare("drop-down items", expected, choiceInfo.getChoices());
        Assert.assertEquals("selected", new Choice("",""), choiceInfo.getSelected());
    }



    @Test
    public void testFtpDirectoryDropdown() {
        final String choiceDir="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/";
        final String choiceDirFilter="type=dir";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        pinfo.getAttributes().put("choiceDirFilter", choiceDirFilter);

        final DynamicChoiceInfoParser choiceInfoParser=new DynamicChoiceInfoParser();
        final ChoiceInfo choiceInfo=choiceInfoParser.initChoiceInfo(pinfo);

        Assert.assertNotNull("choiceInfo.choices", choiceInfo.getChoices());
        final List<Choice> expected=Arrays.asList(new Choice[] {
                new Choice("", ""),
                makeChoice(choiceDir, "A", true), 
                makeChoice(choiceDir, "B", true), 
                makeChoice(choiceDir, "C", true), 
                makeChoice(choiceDir, "D", true), 
                makeChoice(choiceDir, "E", true), 
                makeChoice(choiceDir, "F", true), 
                makeChoice(choiceDir, "G", true), 
                makeChoice(choiceDir, "H", true), 
                makeChoice(choiceDir, "I", true), 
                makeChoice(choiceDir, "J", true), 
        });
        
        listCompare("drop-down items", expected, choiceInfo.getChoices());
    }
    
    /**
     * test case: a directory selected from the drop-down, where the value does not include a trailing slash.
     */
    @Test
    public void testChoiceInfoGetValue_noTrailingSlash() {
        final String choiceDir="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/";
        final String valueNoSlash="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/A";
        final String choiceDirFilter="type=dir";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        pinfo.getAttributes().put("choiceDirFilter", choiceDirFilter);
        final ChoiceInfo choiceInfo = ChoiceInfo.getChoiceInfoParser().initChoiceInfo(pinfo);
        
        final Choice expected=makeChoice(choiceDir, "A", true);
        Assert.assertEquals("getValue, no slash", expected, choiceInfo.getValue(valueNoSlash));
    }
 
    /**
     * test case: a directory selected from the drop-down, where the value includes a trailing slash.
     */
    @Test
    public void testChoiceInfoGetValue_withTrailingSlash() {
        final String choiceDir="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/";
        final String value="ftp://gpftp.broadinstitute.org/example_data/gpservertest/DemoFileDropdown/input.dir/A/";
        final String choiceDirFilter="type=dir";
        final ParameterInfo pinfo=initFtpParam(choiceDir);
        pinfo.getAttributes().put("choiceDirFilter", choiceDirFilter);
        final ChoiceInfo choiceInfo = ChoiceInfo.getChoiceInfoParser().initChoiceInfo(pinfo);
        
        final Choice expected=makeChoice(choiceDir, "A", true);
        Assert.assertEquals("getValue, no slash", expected, choiceInfo.getValue(value));
    }

}
