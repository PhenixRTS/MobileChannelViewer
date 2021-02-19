Pod::Spec.new do |spec|
  spec.version          = '0.1.0'

  spec.name             = 'PhenixClosedCaptions'
  spec.homepage         = "https://phenixrts.com"
  spec.summary          = 'A short description of PhenixClosedCaptions.'
  spec.description      = <<-DESC
TODO: Add long description of the pod here.
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
  spec.source           = { :git => '{LINK TO PhenixClosedCaptions GIT REPO}', :tag => spec.version.to_s }
  spec.ios.deployment_target = '12.0'

  spec.source_files     = 'Source/*.swift', 'Source/Models/*.swift', 'Source/User Interface/*.swift', 'Source/Utils/*.swift'

  spec.xcconfig         = { "ENABLE_BITCODE" => "NO" }

  spec.dependency 'PhenixSdk', '~> 2021.0.0-beta'
end
