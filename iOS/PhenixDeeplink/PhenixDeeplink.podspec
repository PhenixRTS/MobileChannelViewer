Pod::Spec.new do |spec|
  spec.version          = '0.1.0'

  spec.name             = 'PhenixDeeplink'
  spec.homepage         = "https://phenixrts.com"
  spec.summary          = 'A short description of PhenixDebug.'
  spec.description      = <<-DESC
  Support framework providing necessary functionality to parse deep-links.
                       DESC
  spec.homepage         = 'https://phenixrts.com'
  spec.license          = { :type => "Proprietary", :text => <<-LICENSE
                          Copyright 2021 Phenix Real Time Solutions, Inc.
                          Confidential and Proprietary. All rights reserved.
                          By using this code you agree to the Phenix Terms of Service found online here:
                          http://phenixrts.com/terms-of-service.html
                        LICENSE
                        }
  spec.author           = "Phenix Real Time Solutions, Inc."
  spec.source           = { :git => '{LINK TO CLOSED CAPTIONS GIT REPO}', :tag => spec.version.to_s }
  spec.ios.deployment_target = '12.0'

  spec.source_files     = 'Source/*.swift'
end
