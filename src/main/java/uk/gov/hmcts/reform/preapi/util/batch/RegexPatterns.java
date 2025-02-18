package uk.gov.hmcts.reform.preapi.util.batch;

import java.util.regex.Pattern;

public final class RegexPatterns {

    private RegexPatterns() {
    }

    public static final Pattern PATTERN_1 = Pattern.compile(
        "^(?:S28[_\\s-]+)?(?<court>[A-Za-z]+(?:-[A-Za-z]+)*)?[-_\\s]*"
        + "(?:\\d{5})?" 
        + "(?:[A-Za-z]+[-_\\s]+)?"  
        + "(?<date>(?:\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{6}))?(?:-\\d{4})?[-_\\s]*"  
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"  
        + "(?<exhibitRef>T\\d+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<versionType>ORIG|COPY|CPY|ORG|ORI)?(?<versionNumber>\\d*)?[-_\\s]*"  
        + "(?:[A-Za-z0-9]+)?"
        + "(?<ext>\\.(mp4|raw|RAW))?$"  
    );
    

    
    public static final Pattern PATTERN_2 = Pattern.compile(
        "^(?<court>[A-Za-z]+)?[-_\\s]*"            
        + "(?<date>\\d{6})?[-_\\s]*"               
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"        
        + "(?<exhibitRef>[A-Za-z0-9]+)?[-_\\s]*"   
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*" 
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?-?(?<versionNumber>\\d*)?" 
        + "(?<ext>\\.(mp4|raw|RAW))?$"             
    );


    public static final Pattern PATTERN_4 = Pattern.compile(
        "^(?<court>[A-Za-z]+)[-_\\s]*" 
        + "(?<date>\\d{6})[-_\\s]*" 
        + "(?<urn>[A-Za-z0-9]+)[-._\\s]*" 
        + "(?<exhibitRef>[A-Za-z0-9-]+)?[-_\\s]*" 
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?[-_\\s]*" 
        + "(?<versionNumber>\\d*)?" 
        + "(?<ext>\\.(mp4|raw|RAW))?$" 
    );

    public static final Pattern PATTERN_5 = Pattern.compile(
        "^(?<date>\\d{2}-\\d{2}-\\d{4})[-_\\s]*" 
        + "(?:\\d{4})[-_\\s]*"
        + "(?<court>[A-Za-z]+)[-_\\s]*" 
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]" 
        + "(?<exhibitRef>[A-Za-z0-9]*)?[-_\\s]*" 
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?[-_\\s]*" 
        + "(?<versionNumber>\\d*)?" 
        + "(?<ext>\\.(mp4|raw|RAW))?$" 
    );

   
    public static final Pattern PATTERN_10 = Pattern.compile(
        "^(?<court>[A-Za-z]+)?[-_\\s]*"            
        + "(?<date>\\d{6})?[-_\\s]*"               
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"        
        + "(?<exhibitRef>T\\d+)?[-_\\s]*"  
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*" 
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG)?[-_\\s]*" 
        + "(?<versionNumber>\\d+(\\.\\d+)*)?" 
        + "(?<ext>\\.(mp4|raw|RAW))?$"             
    );

    public static final Pattern PATTERN_11 = Pattern.compile(
        "^(?:NEW[_\\s-]+)?(?<court>[A-Za-z]+(?:-[A-Za-z]+)*)?[-_\\s]*"
        + "(?<date>(?:\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{6}))?(?:-\\d{4})?[-_\\s]*"  
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"  
        + "(?<exhibitRef>T\\d+)?[-_\\s]*"  
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<versionType>ORIG|COPY|CPY|ORG|ORI)?(?<versionNumber>\\d*)?[-_\\s]*"  
        + "(?:[A-Za-z0-9]+)?"
        + "(?<ext>\\.(mp4|raw|RAW))?$" 
    );

    public static final Pattern PATTERN_12 = Pattern.compile(
        "^(?:QC)?(?<court>[A-Za-z]+(?:-[A-Za-z]+)*)?[-_\\s]*"
        + "(?<date>(?:\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{6}))?(?:-\\d{4})?[-_\\s]*"  
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"  
        + "(?<exhibitRef>[A-Za-z0-9]+)?[-_\\s]*"  
        + "(?<defendantLastName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<witnessFirstName>[A-Za-z]+)?[-_\\s]*"  
        + "(?<versionType>ORIG|COPY|CPY|ORG|ORI)?(?<versionNumber>\\d*)?[-_\\s]*"  
        + "(?:[A-Za-z0-9]+)?"
        + "(?<ext>\\.(mp4|raw|RAW))?$" 
    );

    public static final Pattern PATTERN_13 = Pattern.compile(
         "^(?<court>[A-Za-z]+(?:-[A-Za-z]+)*)?[-_\\s]*"
        + "(?<date>(?:\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4}|\\d{6}))?[-_\\s]*"
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"
        + "(?<exhibitRef>T\\d+)?[-_\\s]*"
        + "(?<defendantLastName>[A-Za-z]+)"
        + "(?<witnessFirstName>[A-Z][a-z]+)"
        + "\\d*[-_\\s]*"
        + "(?<versionType>ORIG|COPY|CPY|ORG|ORI)"
        + "(?<versionNumber>\\d+\\.\\d+)?[-_\\s]*"
        + "(?<ext>\\.(?:mp4|raw|RAW))$"
    );

    public static final Pattern PATTERN_14 = Pattern.compile(
        "^(?<court>[A-Za-z]+(?:-[A-Za-z]+)*)[-_\\s]*"
        + "(?<date>\\d{6}|\\d{2}-\\d{2}-\\d{4}|\\d{2}/\\d{2}/\\d{4})?[-_\\s]*"
        + "(?<urn>[A-Za-z0-9]+)?[-_\\s]*"
        + "(?<exhibitRef>[A-Za-z0-9]+)?[-_\\s]*"
        + "(?<defendantLastName>[A-Za-z]+)"
        + "(?:[-_\\s]+(?<witnessFirstName>[A-Za-z]+(?:\\sPM)?))?"
        + "[-_\\s]*(?<versionType>ORIG|COPY|CPY|ORG|ORI)?[-_\\s]*(?<versionNumber>\\d+(\\.\\d+)?)?"
        + "\\.(?<ext>mp4|raw|RAW)$"
    );

}
