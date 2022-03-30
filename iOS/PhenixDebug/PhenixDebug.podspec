Pod::Spec.new do |s|
  s.version          = '0.1.0'
  s.name             = 'PhenixDebug'
  s.homepage         = "https://phenixrts.com"
  s.summary          = 'A framework, which provides debug options for PhenixSdk.'
  s.description      = <<-DESC
    A framework, which provides debug options for PhenixSdk.
                       DESC
  s.homepage         = 'https://phenixrts.com'
  s.license          = { :type => "Proprietary", :text => <<-LICENSE
                          Copyright 2022 Phenix Real Time Solutions, Inc.
                          Confidential and Proprietary. All rights reserved.
                          By using this code you agree to the Phenix Terms of Service found online here:
                          http://phenixrts.com/terms-of-service.html
                        LICENSE
                        }
  s.author           = "Phenix Real Time Solutions, Inc."
  s.source           = { :git => '{LINK TO PhenixDebug GIT REPO}', :tag => s.version.to_s }
  s.ios.deployment_target = '13.0'
  s.source_files     = 'Source/**/*.swift'
  s.xcconfig         = { "ENABLE_BITCODE" => "NO" }
  s.dependency 'PhenixCore'
end
