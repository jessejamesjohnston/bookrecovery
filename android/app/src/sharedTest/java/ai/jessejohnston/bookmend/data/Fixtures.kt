package ai.jessejohnston.bookmend.data

/** Excerpts shaped like Google's catalogs, with the awkward cases mixed in. */
object CatalogFixtures {
    val chromeOs = """
    [
      {"channel":"STABLE","name":"Acer Chromebook 514 (CB514-2H, CB514-2HT)","manufacturer":"Acer",
       "hwidmatch":"^SPHERION.*","url":"https://dl.google.com/a_stable.zip","sha1":"aa11",
       "filesize":2147483648,"zipfilesize":1200000000,"version":"16700.68.0"},
      {"channel":"BETA","name":"Acer Chromebook 514 (CB514-2H, CB514-2HT)","manufacturer":"Acer",
       "hwidmatch":"^SPHERION.*","url":"https://dl.google.com/a_beta.zip","sha1":"aa22","filesize":2147483648},
      {"channel":"LTC","name":"Acer Chromebook 514 (CB514-2H, CB514-2HT)","manufacturer":"Acer",
       "hwidmatch":"^SPHERION.*","url":"https://dl.google.com/a_ltc.zip","sha1":"aa33"},
      {"channel":"STABLE","name":"Lenovo Chromebook Plus (14\", 10)","manufacturer":"Lenovo",
       "hwidmatch":"^NAVI-SNEN.*","url":"https://dl.google.com/n.zip","sha1":"bb11","filesize":4294967296},
      {"channel":"STABLE","name":"Broken pattern","manufacturer":"Acme","hwidmatch":"^(UNCLOSED",
       "url":"https://dl.google.com/broken.zip"},
      {"channel":"STABLE","name":"No URL","manufacturer":"Acme","hwidmatch":"^NOURL.*"},
      {"channel":"stable","name":"  Spaced  ","url":"https://dl.google.com/s.zip","sha1":""}
    ]
    """.trimIndent()

    val flex = """
    [{"channel":"STABLE","name":"ChromeOS Flex","manufacturer":"Google","url":"https://dl.google.com/flex.zip","sha1":"ff00"}]
    """.trimIndent()

    val googlebook = """
    [
      {"channel":"STABLE","device":"moonstone","file":"recovery.zip","filesize":19069493760,"manufacturer":"Acer",
       "md5":"b478bb5f4c3ca8512e747897e40f69e3","model":"Acer Googlebook 14","name":"Acer Googlebook 14",
       "url":"https://dl.google.com/device/recovery/moonstone-user/16708002/recovery.zip","zipfilesize":1,
       "buildidentifier":"16708002"},
      {"channel":"STABLE","device":"quartz","manufacturer":"HP","model":"HP Googlebook 14",
       "url":"https://dl.google.com/q.zip","md5":"cc11","filesize":19230966272,"zipfilesize":7700000000},
      {"channel":"STABLE","device":"nourl","manufacturer":"HP","name":"No URL"}
    ]
    """.trimIndent()
}
