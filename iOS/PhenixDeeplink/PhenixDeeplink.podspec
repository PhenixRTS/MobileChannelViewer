Pod::Spec.new do |s|
  s.version          = '0.1.0'
  s.name             = 'PhenixDeeplink'
  s.homepage         = "https://phenixrts.com"
  s.summary          = 'A framework, which provides out-of-box application deep link processing.'
  s.description      = <<-DESC
A framework, which provides out-of-box application deep link processing.
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
  s.source           = { :git => '{LINK TO PhenixDeeplink GIT REPO}', :tag => s.version.to_s }
  s.ios.deployment_target = '12.0'
  s.source_files     = 'Source/**/*.swift'
end
